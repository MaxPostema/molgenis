package org.molgenis.core.ui.data.importer.wizard;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.molgenis.data.meta.model.Package.PACKAGE_SEPARATOR;
import static org.molgenis.util.ApplicationContextProvider.getApplicationContext;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.molgenis.core.ui.wizard.AbstractWizardPage;
import org.molgenis.core.ui.wizard.Wizard;
import org.molgenis.data.DataAction;
import org.molgenis.data.MolgenisDataException;
import org.molgenis.data.RepositoryCollection;
import org.molgenis.data.file.FileRepositoryCollectionFactory;
import org.molgenis.data.importer.ImportService;
import org.molgenis.data.importer.ImportServiceFactory;
import org.molgenis.data.importer.MetadataAction;
import org.molgenis.data.meta.MetaDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;

@Component
public class PackageWizardPage extends AbstractWizardPage {
  private static final long serialVersionUID = 1L;
  private static final Logger LOG = LoggerFactory.getLogger(PackageWizardPage.class);

  private transient FileRepositoryCollectionFactory transientFileRepositoryCollectionFactory;
  private transient ImportServiceFactory transientImportServiceFactory;
  private transient MetaDataService transientMetadataService;

  public PackageWizardPage(
      FileRepositoryCollectionFactory fileRepositoryCollectionFactory,
      ImportServiceFactory importServiceFactory,
      MetaDataService metadataService) {
    this.transientFileRepositoryCollectionFactory = requireNonNull(fileRepositoryCollectionFactory);
    this.transientImportServiceFactory = requireNonNull(importServiceFactory);
    this.transientMetadataService = requireNonNull(metadataService);
  }

  @Override
  public String getTitle() {
    return "Packages";
  }

  @Override
  public String handleRequest(HttpServletRequest request, BindingResult result, Wizard wizard) {
    ImportWizardUtil.validateImportWizard(wizard);
    ImportWizard importWizard = (ImportWizard) wizard;
    String metadataImportOption = importWizard.getMetadataImportOption();
    String dataImportOption = importWizard.getDataImportOption();

    if (dataImportOption != null) {
      try {
        // convert input to database action
        MetadataAction metadataAction = ImportWizardUtil.toMetadataAction(metadataImportOption);
        if (metadataAction == null) {
          throw new IOException("unknown metadata action: " + metadataImportOption);
        }
        DataAction entityDbAction = ImportWizardUtil.toDataAction(dataImportOption);
        if (entityDbAction == null) {
          throw new IOException("unknown data action: " + dataImportOption);
        }

        RepositoryCollection repositoryCollection =
            getFileRepositoryCollectionFactory()
                .createFileRepositoryCollection(importWizard.getFile());

        ImportService importService =
            getImportServiceFactory()
                .getImportService(importWizard.getFile(), repositoryCollection);

        // Do integration test only if there are no previous errors found
        if (metadataAction != MetadataAction.IGNORE
            && !importWizard.getEntitiesImportable().containsValue(false)) {
          // The package name that is selected in the "package selection" page
          String selectedPackage = request.getParameter("selectedPackage");

          // The entities that can be imported
          Map<String, Boolean> entitiesImportable =
              importService.determineImportableEntities(
                  getMetadataService(), repositoryCollection, selectedPackage);

          // The results of the attribute checks are stored in maps with the entityname as key,
          // those need to be updated with the packagename
          updateFieldReports(importWizard, selectedPackage, entitiesImportable);
          // Set the entities that can be imported
          importWizard.setEntitiesImportable(entitiesImportable);

          // The entities that can not be imported. If even one entity can not be imported,
          // everything fails
          List<String> entitiesNotImportable =
              entitiesImportable.entrySet().stream()
                  .filter(entity -> !entity.getValue())
                  .map(Map.Entry::getKey)
                  .collect(toList());

          if (!entitiesNotImportable.isEmpty()) {
            throw new MolgenisDataException(
                "You are trying to upload entities that are not compatible with the already existing entities: "
                    + entitiesNotImportable.toString());
          }
        }

      } catch (RuntimeException | IOException e) {
        ImportWizardUtil.handleException(e, importWizard, result, LOG, dataImportOption);
      }
    }
    return null;
  }

  private void updateFieldReports(
      ImportWizard importWizard, String pack, Map<String, Boolean> entitiesImportable) {
    importWizard.setFieldsAvailable(
        renameKeys(importWizard.getFieldsAvailable(), pack, entitiesImportable));
    importWizard.setFieldsDetected(
        renameKeys(importWizard.getFieldsDetected(), pack, entitiesImportable));
    importWizard.setFieldsRequired(
        renameKeys(importWizard.getFieldsRequired(), pack, entitiesImportable));
    importWizard.setFieldsUnknown(
        renameKeys(importWizard.getFieldsUnknown(), pack, entitiesImportable));
  }

  private Map<String, Collection<String>> renameKeys(
      Map<String, Collection<String>> map, String pack, Map<String, Boolean> entitiesImportable) {
    Map<String, Collection<String>> result = new HashMap<>();
    // if the key is not in the importable entities, this must be caused by the entity being moved
    // into a package
    map.keySet()
        .forEach(
            key -> {
              if (!entitiesImportable.keySet().contains(key)) {
                result.put(pack + PACKAGE_SEPARATOR + key, map.get(key));
              } else {
                result.put(key, map.get(key));
              }
            });
    return result;
  }

  private synchronized FileRepositoryCollectionFactory getFileRepositoryCollectionFactory() {
    if (transientFileRepositoryCollectionFactory == null) {
      transientFileRepositoryCollectionFactory =
          getApplicationContext().getBean(FileRepositoryCollectionFactory.class);
    }
    return transientFileRepositoryCollectionFactory;
  }

  private synchronized ImportServiceFactory getImportServiceFactory() {
    if (transientImportServiceFactory == null) {
      transientImportServiceFactory = getApplicationContext().getBean(ImportServiceFactory.class);
    }
    return transientImportServiceFactory;
  }

  private synchronized MetaDataService getMetadataService() {
    if (transientMetadataService == null) {
      transientMetadataService = getApplicationContext().getBean(MetaDataService.class);
    }
    return transientMetadataService;
  }
}
