package org.molgenis.data.index.bootstrap;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.molgenis.jobs.model.JobExecutionMetaData.FAILED;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.molgenis.data.AbstractMolgenisSpringTest;
import org.molgenis.data.DataService;
import org.molgenis.data.Entity;
import org.molgenis.data.Repository;
import org.molgenis.data.index.IndexActionRegisterService;
import org.molgenis.data.index.IndexService;
import org.molgenis.data.index.job.IndexJobExecution;
import org.molgenis.data.index.job.IndexJobExecutionMetadata;
import org.molgenis.data.index.meta.IndexAction;
import org.molgenis.data.index.meta.IndexActionGroupMetadata;
import org.molgenis.data.index.meta.IndexActionMetadata;
import org.molgenis.data.meta.AttributeType;
import org.molgenis.data.meta.MetaDataService;
import org.molgenis.data.meta.model.Attribute;
import org.molgenis.data.meta.model.AttributeMetadata;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.meta.model.EntityTypeMetadata;
import org.molgenis.data.support.QueryImpl;
import org.molgenis.jobs.model.JobExecutionMetaData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@MockitoSettings(strictness = Strictness.LENIENT)
@ContextConfiguration(classes = {IndexBootstrapperTest.Config.class})
class IndexBootstrapperTest extends AbstractMolgenisSpringTest {
  @Autowired private Config config;
  @Autowired private MetaDataService metaDataService;
  @Autowired private IndexService indexService;
  @Autowired private IndexActionRegisterService indexActionRegisterService;
  @Autowired private DataService dataService;
  @Autowired private AttributeMetadata attributeMetadata;

  private IndexBootstrapper indexBootstrapper;

  @BeforeEach
  void beforeMethod() {
    config.resetMocks();

    indexBootstrapper =
        new IndexBootstrapper(
            metaDataService,
            indexService,
            indexActionRegisterService,
            dataService,
            attributeMetadata);
  }

  @Test
  void testStartupNoIndex() {
    @SuppressWarnings("unchecked")
    Repository<Entity> repo1 = mock(Repository.class);
    EntityType entityType1 = mock(EntityType.class);
    when(repo1.getEntityType()).thenReturn(entityType1);
    @SuppressWarnings("unchecked")
    Repository<Entity> repo2 = mock(Repository.class);
    EntityType entityType2 = mock(EntityType.class);
    when(repo2.getEntityType()).thenReturn(entityType2);
    @SuppressWarnings("unchecked")
    Repository<Entity> repo3 = mock(Repository.class);
    EntityType entityType3 = mock(EntityType.class);
    when(repo3.getEntityType()).thenReturn(entityType3);

    List<Repository<Entity>> repos = Arrays.asList(repo1, repo2, repo3);

    when(indexService.hasIndex(attributeMetadata)).thenReturn(false);
    when(metaDataService.getRepositories()).thenReturn(repos.stream());
    indexBootstrapper.bootstrap();

    // verify that new jobs are registered for all repos
    verify(indexActionRegisterService).register(entityType1, null);
    verify(indexActionRegisterService).register(entityType2, null);
    verify(indexActionRegisterService).register(entityType3, null);
  }

  @Test
  void testStartupFailedIndexJobs() {
    when(indexService.hasIndex(attributeMetadata)).thenReturn(true);
    IndexJobExecution indexJobExecution = mock(IndexJobExecution.class);
    when(indexJobExecution.getIndexActionJobID()).thenReturn("id");
    IndexAction action = mock(IndexAction.class);
    when(action.getEntityTypeId()).thenReturn("myEntityTypeName");
    when(action.getEntityId()).thenReturn("1");
    when(action.getId()).thenReturn("actionId");
    EntityType entityType = mock(EntityType.class);
    when(dataService.findOneById(
            EntityTypeMetadata.ENTITY_TYPE_META_DATA, "myEntityTypeName", EntityType.class))
        .thenReturn(entityType);
    Attribute idAttribute = mock(Attribute.class);
    when(idAttribute.getDataType()).thenReturn(AttributeType.INT);
    when(entityType.getIdAttribute()).thenReturn(idAttribute);
    when(dataService.findAll(
            IndexJobExecutionMetadata.INDEX_JOB_EXECUTION,
            new QueryImpl<IndexJobExecution>().eq(JobExecutionMetaData.STATUS, FAILED),
            IndexJobExecution.class))
        .thenReturn(Stream.of(indexJobExecution));
    when(dataService.findAll(
            IndexActionMetadata.INDEX_ACTION,
            new QueryImpl<IndexAction>().eq(IndexActionMetadata.INDEX_ACTION_GROUP_ATTR, "id"),
            IndexAction.class))
        .thenReturn(Stream.of(action));

    indexBootstrapper.bootstrap();

    // verify that we are not passing through the "missing index" code
    verify(metaDataService, never()).getRepositories();
    // verify that a new job is registered for the failed one
    verify(indexActionRegisterService).register(entityType, 1);

    // verify that the failed job and corresponding actions are cleaned up
    verify(dataService).delete(IndexJobExecutionMetadata.INDEX_JOB_EXECUTION, indexJobExecution);

    ArgumentCaptor<Stream<Object>> captor = ArgumentCaptor.forClass(Stream.class);
    verify(dataService).deleteAll(eq(IndexActionMetadata.INDEX_ACTION), captor.capture());
    assertEquals(singletonList("actionId"), captor.getValue().collect(toList()));
    verify(dataService).deleteById(IndexActionGroupMetadata.INDEX_ACTION_GROUP, "id");
  }

  @Test
  void testStartupFailedIndexJobsUnknownEntityType() {
    when(indexService.hasIndex(attributeMetadata)).thenReturn(true);
    IndexJobExecution indexJobExecution = mock(IndexJobExecution.class);
    when(indexJobExecution.getIndexActionJobID()).thenReturn("id");
    IndexAction action = mock(IndexAction.class);
    when(action.getEntityTypeId()).thenReturn("myEntityTypeName");
    when(action.getEntityId()).thenReturn("1");
    EntityType entityType = mock(EntityType.class);
    when(dataService.findOneById(
            EntityTypeMetadata.ENTITY_TYPE_META_DATA, "myEntityTypeName", EntityType.class))
        .thenReturn(null);
    Attribute idAttribute = mock(Attribute.class);
    when(idAttribute.getDataType()).thenReturn(AttributeType.INT);
    when(entityType.getIdAttribute()).thenReturn(idAttribute);
    when(dataService.findAll(
            IndexJobExecutionMetadata.INDEX_JOB_EXECUTION,
            new QueryImpl<IndexJobExecution>().eq(JobExecutionMetaData.STATUS, FAILED),
            IndexJobExecution.class))
        .thenReturn(Stream.of(indexJobExecution));
    when(dataService.findAll(
            IndexActionMetadata.INDEX_ACTION,
            new QueryImpl<IndexAction>().eq(IndexActionMetadata.INDEX_ACTION_GROUP_ATTR, "id"),
            IndexAction.class))
        .thenReturn(Stream.of(action));

    indexBootstrapper.bootstrap();

    // verify that we are not passing through the "missing index" code
    verify(metaDataService, never()).getRepositories();
    // verify that a new job is registered for the failed one
    verify(indexActionRegisterService, times(0)).register(entityType, 1);
  }

  @Test
  void testStartupAllIsFine() {
    when(indexService.hasIndex(attributeMetadata)).thenReturn(true);

    when(dataService.findAll(
            IndexJobExecutionMetadata.INDEX_JOB_EXECUTION,
            new QueryImpl<IndexJobExecution>().eq(JobExecutionMetaData.STATUS, FAILED),
            IndexJobExecution.class))
        .thenReturn(Stream.empty());
    indexBootstrapper.bootstrap();

    // verify that no new jobs are registered
    verify(indexActionRegisterService, never()).register(any(EntityType.class), any());
  }

  @SuppressWarnings("java:S5979") // mocks are initialized
  @Configuration
  static class Config {
    @Mock IndexService indexService;

    @Mock IndexActionRegisterService indexActionRegisterService;

    @Mock MetaDataService metaDataService;

    @Mock AttributeMetadata attributeMetadata;

    Config() {
      org.mockito.MockitoAnnotations.initMocks(this);
    }

    @Bean
    IndexService indexService() {
      return indexService;
    }

    @Bean
    IndexActionRegisterService indexActionRegisterService() {
      return indexActionRegisterService;
    }

    @Bean
    MetaDataService metaDataService() {
      return metaDataService;
    }

    void resetMocks() {
      reset(indexService, indexActionRegisterService, metaDataService, attributeMetadata);
    }
  }
}
