package org.molgenis.semanticmapper.service.impl;

import static com.google.common.collect.Sets.newLinkedHashSet;
import static com.google.common.collect.Streams.stream;
import static java.lang.Double.parseDouble;
import static java.lang.Math.round;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.molgenis.data.DataConverter.toBoolean;
import static org.molgenis.data.meta.AttributeType.DATE;
import static org.molgenis.data.meta.AttributeType.DATE_TIME;
import static org.molgenis.data.meta.AttributeType.DECIMAL;
import static org.molgenis.data.meta.AttributeType.INT;
import static org.molgenis.data.meta.AttributeType.LONG;

import com.google.common.base.Throwables;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityManager;
import org.molgenis.data.meta.AttributeType;
import org.molgenis.data.meta.IllegalAttributeTypeException;
import org.molgenis.data.meta.model.Attribute;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.js.magma.JsMagmaScriptContext;
import org.molgenis.js.magma.JsMagmaScriptContextHolder;
import org.molgenis.js.magma.WithJsMagmaScriptContext;
import org.molgenis.security.core.runas.RunAsSystem;
import org.molgenis.semanticmapper.algorithmgenerator.bean.GeneratedAlgorithm;
import org.molgenis.semanticmapper.algorithmgenerator.service.AlgorithmGeneratorService;
import org.molgenis.semanticmapper.mapping.model.AttributeMapping;
import org.molgenis.semanticmapper.mapping.model.AttributeMapping.AlgorithmState;
import org.molgenis.semanticmapper.mapping.model.EntityMapping;
import org.molgenis.semanticmapper.service.AlgorithmService;
import org.molgenis.semanticsearch.explain.bean.EntityTypeSearchResults;
import org.molgenis.semanticsearch.explain.bean.ExplainedAttribute;
import org.molgenis.semanticsearch.semantic.Hits;
import org.molgenis.semanticsearch.service.SemanticSearchService;
import org.molgenis.util.UnexpectedEnumException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlgorithmServiceImpl implements AlgorithmService {
  private static final Logger LOG = LoggerFactory.getLogger(AlgorithmServiceImpl.class);

  private final SemanticSearchService semanticSearchService;
  private final AlgorithmGeneratorService algorithmGeneratorService;
  private final EntityManager entityManager;

  public AlgorithmServiceImpl(
      SemanticSearchService semanticSearchService,
      AlgorithmGeneratorService algorithmGeneratorService,
      EntityManager entityManager) {
    this.semanticSearchService = requireNonNull(semanticSearchService);
    this.algorithmGeneratorService = requireNonNull(algorithmGeneratorService);
    this.entityManager = requireNonNull(entityManager);
  }

  @Override
  public String generateAlgorithm(
      Attribute targetAttribute,
      EntityType targetEntityType,
      List<Attribute> sourceAttributes,
      EntityType sourceEntityType) {
    return algorithmGeneratorService.generate(
        targetAttribute, sourceAttributes, targetEntityType, sourceEntityType);
  }

  @Override
  public void copyAlgorithms(EntityMapping sourceEntityMapping, EntityMapping targetEntityMapping) {
    sourceEntityMapping
        .getAttributeMappings()
        .forEach(attributeMapping -> copyAlgorithm(attributeMapping, targetEntityMapping));
  }

  private void copyAlgorithm(AttributeMapping attributeMapping, EntityMapping targetEntityMapping) {
    AttributeMapping attributeMappingCopy = AttributeMapping.createCopy(attributeMapping);
    attributeMappingCopy.setIdentifier(null);
    attributeMappingCopy.setAlgorithmState(AlgorithmState.DISCUSS);
    targetEntityMapping.addAttributeMapping(attributeMappingCopy);
  }

  @Override
  @RunAsSystem
  public void autoGenerateAlgorithm(
      EntityType sourceEntityType, EntityType targetEntityType, EntityMapping mapping) {
    EntityTypeSearchResults entityTypeSearchResults =
        semanticSearchService.findAttributes(sourceEntityType, targetEntityType);
    entityTypeSearchResults
        .getAttributeSearchResults()
        .forEach(
            attributeSearchResults -> {
              Attribute targetAttribute = attributeSearchResults.getAttribute();
              LOG.debug(
                  "createAttributeMappingIfOnlyOneMatch: target= {}", targetAttribute.getName());
              Hits<ExplainedAttribute> relevantAttributes = attributeSearchResults.getHits();
              GeneratedAlgorithm generatedAlgorithm =
                  algorithmGeneratorService.generate(
                      targetAttribute, relevantAttributes, targetEntityType, sourceEntityType);

              if (StringUtils.isNotBlank(generatedAlgorithm.getAlgorithm())) {
                AttributeMapping attributeMapping =
                    mapping.addAttributeMapping(targetAttribute.getName());
                attributeMapping.setAlgorithm(generatedAlgorithm.getAlgorithm());
                attributeMapping
                    .getSourceAttributes()
                    .addAll(generatedAlgorithm.getSourceAttributes());
                attributeMapping.setAlgorithmState(generatedAlgorithm.getAlgorithmState());
                LOG.debug(
                    "Creating attribute mapping: "
                        + targetAttribute.getName()
                        + " = "
                        + generatedAlgorithm.getAlgorithm());
              }
            });
  }

  @Override
  @WithJsMagmaScriptContext
  public Iterable<AlgorithmEvaluation> applyAlgorithm(
      Attribute targetAttribute, String algorithm, Iterable<Entity> sourceEntities) {
    var context = JsMagmaScriptContextHolder.getContext();
    return stream(sourceEntities)
        .map(
            entity -> {
              AlgorithmEvaluation algorithmResult = new AlgorithmEvaluation(entity);
              Object derivedValue;
              try {
                context.bind(entity);
                Object result = context.eval(algorithm);
                derivedValue = convert(result, targetAttribute);
              } catch (RuntimeException e) {
                if (e.getMessage() == null) {
                  return algorithmResult.errorMessage(
                      "Applying an algorithm on a null source value caused an exception. Is the target attribute required?");
                }
                return algorithmResult.errorMessage(e.getLocalizedMessage());
              }
              return algorithmResult.value(derivedValue);
            })
        .collect(toList());
  }

  @Override
  public void bind(Entity sourceEntity) {
    JsMagmaScriptContext context = JsMagmaScriptContextHolder.getContext();
    context.bind(sourceEntity);
  }

  @Override
  public Object apply(AttributeMapping attributeMapping) {
    var context = JsMagmaScriptContextHolder.getContext();
    try {
      return Optional.ofNullable(attributeMapping.getAlgorithm())
          .filter(not(StringUtils::isEmpty))
          .map(context::eval)
          .map(value -> convert(value, attributeMapping.getTargetAttribute()))
          .orElse(null);
    } catch (Exception thrown) {
      throw new AlgorithmException(Throwables.getRootCause(thrown));
    }
  }

  @Override
  public Collection<String> getSourceAttributeNames(String algorithmScript) {
    Collection<String> result = emptyList();
    if (!isEmpty(algorithmScript)) {
      result = findMatchesForPattern(algorithmScript, "\\$\\('([^\\$\\(\\)]+)'\\)");
      if (result.isEmpty()) {
        result = findMatchesForPattern(algorithmScript, "\\$\\(([^\\$\\(\\)]+)\\)");
      }
    }
    return result;
  }

  private static Collection<String> findMatchesForPattern(
      String algorithmScript, String patternString) {
    LinkedHashSet<String> result = newLinkedHashSet();
    Matcher matcher = Pattern.compile(patternString).matcher(algorithmScript);
    while (matcher.find()) {
      result.add(matcher.group(1).split("\\.")[0]);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private Object convert(Object value, Attribute attr) {
    Object convertedValue;
    AttributeType attrType = attr.getDataType();
    switch (attrType) {
      case BOOL:
        convertedValue = value != null ? toBoolean(value) : null;
        break;
      case CATEGORICAL:
      case XREF:
      case FILE:
        convertedValue =
            value != null
                ? entityManager.getReference(
                    attr.getRefEntity(), convert(value, attr.getRefEntity().getIdAttribute()))
                : null;
        break;
      case CATEGORICAL_MREF:
      case MREF:
      case ONE_TO_MANY:
        Collection<Object> valueIds = (Collection<Object>) value;

        convertedValue =
            valueIds.stream()
                .map(
                    valueId ->
                        entityManager.getReference(
                            attr.getRefEntity(),
                            convert(valueId, attr.getRefEntity().getIdAttribute())))
                .collect(toList());
        break;
      case DATE:
        convertedValue = convertToDate(value);
        break;
      case DATE_TIME:
        convertedValue = convertToDateTime(value);
        break;
      case DECIMAL:
        convertedValue = convertToDouble(value);
        break;
      case EMAIL:
      case ENUM:
      case HTML:
      case HYPERLINK:
      case SCRIPT:
      case STRING:
      case TEXT:
        convertedValue = value != null ? value.toString() : null;
        break;
      case INT:
        convertedValue = convertToInteger(value);
        break;
      case LONG:
        convertedValue = convertToLong(value);
        break;
      case COMPOUND:
        throw new IllegalAttributeTypeException(attrType);
      default:
        throw new UnexpectedEnumException(attrType);
    }

    return convertedValue;
  }

  LocalDate convertToDate(Object value) {
    try {
      return value != null
          ? Instant.ofEpochMilli(Double.valueOf(value.toString()).longValue())
              .atZone(ZoneId.systemDefault())
              .toLocalDate()
          : null;
    } catch (NumberFormatException e) {
      LOG.debug("", e);
      throw new AlgorithmException(
          format("'%s' can't be converted to type '%s'", value.toString(), DATE.toString()));
    }
  }

  private Instant convertToDateTime(Object value) {
    try {
      return value != null
          ? Instant.ofEpochMilli(Double.valueOf(value.toString()).longValue())
          : null;
    } catch (NumberFormatException e) {
      LOG.debug("", e);
      throw new AlgorithmException(
          format("'%s' can't be converted to type '%s'", value.toString(), DATE_TIME.toString()));
    }
  }

  private Double convertToDouble(Object value) {
    try {
      return value != null ? parseDouble(value.toString()) : null;
    } catch (NumberFormatException e) {
      LOG.debug("", e);
      throw new AlgorithmException(
          format("'%s' can't be converted to type '%s'", value.toString(), DECIMAL.toString()));
    }
  }

  private Integer convertToInteger(Object value) {
    Integer convertedValue;
    try {
      convertedValue = value != null ? toIntExact(round(parseDouble(value.toString()))) : null;
    } catch (NumberFormatException e) {
      LOG.debug("", e);
      throw new AlgorithmException(
          format("'%s' can't be converted to type '%s'", value.toString(), INT.toString()));
    } catch (ArithmeticException e) {
      LOG.debug("", e);
      throw new AlgorithmException(
          format(
              "'%s' is larger than the maximum allowed value for type '%s'",
              value.toString(), INT.toString()));
    }
    return convertedValue;
  }

  private Long convertToLong(Object value) {
    Long convertedValue;
    try {
      convertedValue = value != null ? round(parseDouble(value.toString())) : null;
    } catch (NumberFormatException e) {
      LOG.debug("", e);
      throw new AlgorithmException(
          format("'%s' can't be converted to type '%s'", value.toString(), LONG.toString()));
    }
    return convertedValue;
  }
}
