package org.molgenis.data.postgresql;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockitoSession;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.STRICT_STUBS;
import static org.molgenis.data.QueryRule.Operator.EQUALS;
import static org.molgenis.data.meta.AttributeType.INT;
import static org.molgenis.data.meta.AttributeType.LONG;
import static org.molgenis.data.meta.AttributeType.MREF;
import static org.molgenis.data.meta.AttributeType.ONE_TO_MANY;
import static org.molgenis.data.meta.AttributeType.STRING;
import static org.molgenis.data.meta.AttributeType.XREF;
import static org.molgenis.data.postgresql.PostgreSqlExceptionTranslator.VALUE_TOO_LONG_MSG;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.molgenis.data.Entity;
import org.molgenis.data.Query;
import org.molgenis.data.QueryRule;
import org.molgenis.data.meta.AttributeType;
import org.molgenis.data.meta.model.Attribute;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.postgresql.PostgreSqlEntityFactory.EntityMapper;
import org.molgenis.data.validation.MolgenisValidationException;
import org.molgenis.util.UnexpectedEnumException;
import org.molgenis.validation.ConstraintViolation;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings("java:S5979") // mocks are initialized
class PostgreSqlRepositoryTest {
  private PostgreSqlRepository postgreSqlRepo;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private PostgreSqlEntityFactory postgreSqlEntityFactory;
  @Mock private DataSource dataSource;
  @Mock private EntityType entityType;
  @Mock private Query<Entity> query;
  @Mock private EntityMapper rowMapper;

  private MockitoSession mockitoSession;

  @BeforeEach
  void setUpBeforeMethod() throws Exception {
    mockitoSession = mockitoSession().initMocks(this).strictness(STRICT_STUBS).startMocking();
    postgreSqlRepo =
        new PostgreSqlRepository(postgreSqlEntityFactory, jdbcTemplate, dataSource, entityType);
  }

  @AfterEach
  void afterMethod() {
    mockitoSession.finishMocking();
  }

  // TODO test all query operators for one-to-many
  @Test
  void countQueryOneToManyEquals() throws Exception {
    String oneToManyAttrName = "oneToManyAttr";
    String refIdAttrName = "refEntityId";
    Attribute refIdAttr = mock(Attribute.class);
    when(refIdAttr.getName()).thenReturn(refIdAttrName);
    when(refIdAttr.getDataType()).thenReturn(INT);

    String xrefAttrName = "xrefAttr";
    Attribute xrefAttr = mock(Attribute.class);
    when(xrefAttr.getName()).thenReturn(xrefAttrName);

    EntityType refEntityMeta = mock(EntityType.class);
    when(refEntityMeta.getId()).thenReturn("refEntityId");
    when(refEntityMeta.getIdAttribute()).thenReturn(refIdAttr);

    String idAttrName = "entityId";
    Attribute idAttr = mock(Attribute.class);
    when(idAttr.getName()).thenReturn(idAttrName);

    Attribute oneToManyAttr = mock(Attribute.class);
    when(oneToManyAttr.getName()).thenReturn(oneToManyAttrName);
    when(oneToManyAttr.getDataType()).thenReturn(ONE_TO_MANY);
    when(oneToManyAttr.getRefEntity()).thenReturn(refEntityMeta);
    when(oneToManyAttr.isMappedBy()).thenReturn(true);
    when(oneToManyAttr.getMappedBy()).thenReturn(xrefAttr);

    when(entityType.getId()).thenReturn("entityId");
    when(entityType.getIdAttribute()).thenReturn(idAttr);
    doReturn(oneToManyAttr).when(entityType).getAttribute(oneToManyAttrName);

    int queryValue = 2;
    QueryRule queryRule = new QueryRule(oneToManyAttrName, EQUALS, queryValue);
    when(query.getRules()).thenReturn(singletonList(queryRule));

    String sql =
        "SELECT COUNT(DISTINCT this.\"entityId\") FROM \"entityId#fc2928f6\" AS this LEFT JOIN \"refEntityId#07f902bf\" AS \"oneToManyAttr_filter1\" ON (this.\"entityId\" = \"oneToManyAttr_filter1\".\"xrefAttr\") WHERE \"oneToManyAttr_filter1\".\"refEntityId\" = ?";
    long count = 123L;
    when(jdbcTemplate.queryForObject(sql, new Object[] {queryValue}, Long.class)).thenReturn(count);

    assertEquals(count, postgreSqlRepo.count(query));
  }

  @Test
  void findAllQueryOneToManyEquals() throws Exception {
    String oneToManyAttrName = "oneToManyAttr";
    String refIdAttrName = "refEntityId";
    Attribute refIdAttr = mock(Attribute.class);
    when(refIdAttr.getName()).thenReturn(refIdAttrName);
    when(refIdAttr.getDataType()).thenReturn(INT);
    when(refIdAttr.isUnique()).thenReturn(true);

    String xrefAttrName = "xrefAttr";
    Attribute xrefAttr = mock(Attribute.class);
    when(xrefAttr.getName()).thenReturn(xrefAttrName);

    EntityType refEntityMeta = mock(EntityType.class);
    when(refEntityMeta.getId()).thenReturn("refEntityId");
    when(refEntityMeta.getIdAttribute()).thenReturn(refIdAttr);
    doReturn(refIdAttr).when(refEntityMeta).getAttribute(refIdAttrName);

    String idAttrName = "entityId";
    Attribute idAttr = mock(Attribute.class);
    when(idAttr.getName()).thenReturn(idAttrName);
    when(idAttr.getDataType()).thenReturn(STRING);

    Attribute oneToManyAttr = mock(Attribute.class);
    when(oneToManyAttr.getName()).thenReturn(oneToManyAttrName);
    when(oneToManyAttr.getDataType()).thenReturn(ONE_TO_MANY);
    when(oneToManyAttr.getRefEntity()).thenReturn(refEntityMeta);
    when(oneToManyAttr.isMappedBy()).thenReturn(true);
    when(oneToManyAttr.getMappedBy()).thenReturn(xrefAttr);

    when(entityType.getId()).thenReturn("entityId");
    when(entityType.getIdAttribute()).thenReturn(idAttr);
    when(entityType.getAttribute("entityId")).thenReturn(idAttr);
    doReturn(oneToManyAttr).when(entityType).getAttribute(oneToManyAttrName);
    when(entityType.getAtomicAttributes()).thenReturn(newArrayList(idAttr, oneToManyAttr));
    EntityType entityType = this.entityType;
    postgreSqlRepo =
        new PostgreSqlRepository(postgreSqlEntityFactory, jdbcTemplate, dataSource, entityType);

    int queryValue = 2;
    QueryRule queryRule = new QueryRule(oneToManyAttrName, EQUALS, queryValue);
    when(query.getRules()).thenReturn(singletonList(queryRule));

    String sql =
        "SELECT DISTINCT this.\"entityId\", (SELECT array_agg(\"refEntityId\" ORDER BY \"refEntityId\" ASC) FROM \"refEntityId#07f902bf\" WHERE this.\"entityId\" = \"refEntityId#07f902bf\".\"xrefAttr\") AS \"oneToManyAttr\" FROM \"entityId#fc2928f6\" AS this LEFT JOIN \"refEntityId#07f902bf\" AS \"oneToManyAttr_filter1\" ON (this.\"entityId\" = \"oneToManyAttr_filter1\".\"xrefAttr\") WHERE \"oneToManyAttr_filter1\".\"refEntityId\" = ? ORDER BY \"entityId\" ASC LIMIT 1000";

    when(postgreSqlEntityFactory.createRowMapper(entityType, null)).thenReturn(rowMapper);
    Entity entity0 = mock(Entity.class);
    when(jdbcTemplate.query(sql, new Object[] {queryValue}, rowMapper))
        .thenReturn(singletonList(entity0));
    assertEquals(singletonList(entity0), postgreSqlRepo.findAll(query).collect(toList()));
  }

  @Test
  void testUpdateEntitiesExist() {
    Attribute idAttr = mock(Attribute.class);
    when(idAttr.getName()).thenReturn("attr");
    when(idAttr.getDataType()).thenReturn(STRING);

    when(entityType.getIdAttribute()).thenReturn(idAttr);
    when(entityType.getAtomicAttributes()).thenReturn(singletonList(idAttr));
    when(entityType.getId()).thenReturn("entity");

    Entity entity0 = mock(Entity.class);
    Entity entity1 = mock(Entity.class);

    when(jdbcTemplate.batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class)))
        .thenReturn(new int[] {2});

    postgreSqlRepo.update(Stream.of(entity0, entity1));
    verify(jdbcTemplate).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));
    verifyNoMoreInteractions(jdbcTemplate);
  }

  @SuppressWarnings("unchecked")
  @Test
  void testUpdateEntityDoesNotExist() {
    Attribute idAttr = mock(Attribute.class);
    when(idAttr.getName()).thenReturn("attr");
    when(idAttr.getDataType()).thenReturn(STRING);

    when(entityType.getIdAttribute()).thenReturn(idAttr);
    when(entityType.getAtomicAttributes()).thenReturn(singletonList(idAttr));
    when(entityType.getId()).thenReturn("entity");
    when(entityType.getAttribute("attr")).thenReturn(idAttr);

    Entity entity0 = mock(Entity.class);
    when(entity0.getIdValue()).thenReturn("id0");
    Entity entity1 = mock(Entity.class);
    when(entity1.getIdValue()).thenReturn("id1");

    when(jdbcTemplate.query(any(String.class), any(Object[].class), (RowMapper) isNull()))
        .thenReturn(singletonList(entity0));
    when(jdbcTemplate.batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class)))
        .thenReturn(new int[] {1});

    Exception exception =
        assertThrows(
            MolgenisValidationException.class,
            () -> postgreSqlRepo.update(Stream.of(entity0, entity1)));
    assertThat(exception.getMessage())
        .containsPattern("Cannot update \\[entity\\] with id \\[id1\\] because it does not exist");
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  void testAddEntityNull() {
    Entity entity = null;
    Exception exception =
        assertThrows(NullPointerException.class, () -> postgreSqlRepo.add(entity));
    assertThat(exception.getMessage())
        .containsPattern("PostgreSqlRepository.add\\(\\) failed: entity was null");
  }

  static Object[][] provideValidMrefIds() throws SQLException {
    ResultSet stringRow = mock(ResultSet.class);
    doReturn("idValue").when(stringRow).getString(1);
    doReturn("refValue").when(stringRow).getString(3);

    ResultSet intRow = mock(ResultSet.class);
    doReturn(1).when(intRow).getInt(1);
    doReturn(2).when(intRow).getInt(3);

    ResultSet longRow = mock(ResultSet.class);
    doReturn(1L).when(longRow).getLong(1);
    doReturn(2L).when(longRow).getLong(3);

    return new Object[][] {
      {STRING, STRING, stringRow, ImmutableMap.of("idValue", ImmutableList.of("refValue"))},
      {INT, INT, intRow, ImmutableMap.of(1, ImmutableList.of(2))},
      {LONG, LONG, longRow, ImmutableMap.of(1L, ImmutableList.of(2L))}
    };
  }

  @Test
  void testMrefValueTooLong() {
    Attribute attr = mock(Attribute.class);
    when(attr.getName()).thenReturn("mref_attr");

    Attribute idAttr = mock(Attribute.class);
    when(idAttr.getName()).thenReturn("id");
    when(entityType.getIdAttribute()).thenReturn(idAttr);
    when(entityType.getId()).thenReturn("test_entity");

    MolgenisValidationException mve =
        new MolgenisValidationException(new ConstraintViolation(VALUE_TOO_LONG_MSG));

    when(jdbcTemplate.batchUpdate(any(), any(BatchPreparedStatementSetter.class))).thenThrow(mve);

    HashMap<String, Object> value = newHashMap();
    value.put("mref_attr", "TOOLONG");

    Exception exception =
        assertThrows(
            MolgenisValidationException.class,
            () -> postgreSqlRepo.addMrefs(newArrayList(value), attr));
    assertThat(exception.getMessage())
        .containsPattern(
            "One of the mref values in entity type \\[test_entity\\] attribute \\[mref_attr\\] is too long.");
  }

  @ParameterizedTest
  @MethodSource("provideValidMrefIds")
  void testMrefIdRowCallbackHandlerString(
      AttributeType idType, AttributeType refType, ResultSet row, Map<Object, List<Object>> result)
      throws SQLException {
    Multimap<Object, Object> mrefIDs = ArrayListMultimap.create();
    RowCallbackHandler mrefIdRowCallbackHandler =
        postgreSqlRepo.getJunctionTableRowCallbackHandler(idType, refType, mrefIDs);
    mrefIdRowCallbackHandler.processRow(row);

    assertEquals(result, mrefIDs.asMap());
  }

  static Object[][] provideInvalidMrefIds() {
    return new Object[][] {{XREF, STRING}, {INT, MREF}};
  }

  @ParameterizedTest
  @MethodSource("provideInvalidMrefIds")
  void testMrefIdRowCallbackHandlerInvalidType(AttributeType idType, AttributeType refType)
      throws SQLException {
    RowCallbackHandler mrefIdRowCallbackHandler =
        postgreSqlRepo.getJunctionTableRowCallbackHandler(
            idType, refType, ArrayListMultimap.create());
    assertThrows(
        UnexpectedEnumException.class,
        () -> mrefIdRowCallbackHandler.processRow(mock(ResultSet.class)));
  }
}
