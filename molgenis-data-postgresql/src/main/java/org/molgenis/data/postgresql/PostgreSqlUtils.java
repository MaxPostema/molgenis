package org.molgenis.data.postgresql;

import org.molgenis.MolgenisFieldTypes;
import org.molgenis.data.Entity;
import org.molgenis.data.MolgenisDataException;
import org.molgenis.data.meta.model.AttributeMetaData;
import org.molgenis.file.model.FileMeta;

import java.util.Date;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * PostgreSQL utilities such as entity value to PostgreSQL value conversion.
 */
class PostgreSqlUtils
{
	private PostgreSqlUtils()
	{
	}

	/**
	 * Returns the PostgreSQL value for the given entity attribute
	 *
	 * @param entity entity
	 * @param attr   attribute
	 * @return PostgreSQL value
	 */
	static Object getPostgreSqlValue(Entity entity, AttributeMetaData attr)
	{
		String attrName = attr.getName();
		MolgenisFieldTypes.AttributeType attrType = attr.getDataType();

		switch (attrType)
		{
			case BOOL:
				return entity.getBoolean(attrName);
			case CATEGORICAL:
			case XREF:
				Entity xrefEntity = entity.getEntity(attrName);
				return xrefEntity != null ? getPostgreSqlValue(xrefEntity,
						xrefEntity.getEntityMetaData().getIdAttribute()) : null;
			case CATEGORICAL_MREF:
			case MREF:
				Iterable<Entity> entities = entity.getEntities(attrName);
				return stream(entities.spliterator(), false).map(mrefEntity -> getPostgreSqlValue(mrefEntity,
						mrefEntity.getEntityMetaData().getIdAttribute())).collect(toList());
			case DATE:
				Date date = entity.getUtilDate(attrName);
				return date != null ? new java.sql.Date(date.getTime()) : null;
			case DATE_TIME:
				Date dateTime = entity.getUtilDate(attrName);
				return dateTime != null ? new java.sql.Timestamp(dateTime.getTime()) : null;
			case DECIMAL:
				return entity.getDouble(attrName);
			case EMAIL:
			case ENUM:
			case HTML:
			case HYPERLINK:
			case SCRIPT:
			case STRING:
			case TEXT:
				return entity.getString(attrName);
			case FILE:
				FileMeta fileEntity = entity.getEntity(attrName, FileMeta.class);
				return fileEntity != null ? getPostgreSqlValue(fileEntity,
						fileEntity.getEntityMetaData().getIdAttribute()) : null;
			case INT:
				return entity.getInt(attrName);
			case LONG:
				return entity.getLong(attrName);
			case COMPOUND:
				throw new RuntimeException(format("Illegal attribute type [%s]", attrType.toString()));
			default:
				throw new RuntimeException(format("Unknown attribute type [%s]", attrType.toString()));
		}
	}

	/**
	 * Returns the PostgreSQL value for the given entity attribute
	 *
	 * @param queryValue value of the type that matches the attribute type
	 * @param attr       attribute
	 * @return PostgreSQL value
	 */
	static Object getPostgreSqlQueryValue(Object queryValue, AttributeMetaData attr)
	{
		String attrName = attr.getName();
		MolgenisFieldTypes.AttributeType attrType = attr.getDataType();

		switch (attrType)
		{
			case BOOL:
				if (queryValue != null && !(queryValue instanceof Boolean))
				{
					throw new MolgenisDataException(
							format("Attribute [%s] query value is of type [%s] instead of [%s]", attrName,
									queryValue.getClass().getSimpleName(), Boolean.class.getSimpleName()));
				}
				return queryValue;
			case CATEGORICAL:
			case FILE:
			case XREF:
				// queries values referencing an entity can either be the entity itself or the entity id
				if (queryValue != null)
				{
					if (queryValue instanceof Entity)
					{
						queryValue = ((Entity) queryValue).getIdValue();
					}
					return getPostgreSqlQueryValue(queryValue, attr.getRefEntity().getIdAttribute());
				}
				else
				{
					return null;
				}
			case CATEGORICAL_MREF:
			case MREF:
				// queries values referencing entities can either be an entity iterable or entity id iterable
				if (!(queryValue instanceof Iterable<?>))
				{
					throw new MolgenisDataException(
							format("Attribute [%s] query value is of type [%s] instead of [%s]", attrName,
									queryValue.getClass().getSimpleName(), Iterable.class.getSimpleName()));
				}
				Stream<Object> queryIdValues = stream(((Iterable<?>) queryValue).spliterator(), false)
						.map(queryMrefValue -> queryMrefValue instanceof Entity ? ((Entity) queryMrefValue)
								.getIdValue() : queryMrefValue);
				return queryIdValues.map(queryIdValue -> getPostgreSqlQueryValue(queryIdValue,
						attr.getRefEntity().getIdAttribute()))
						.collect(toList());
			case DATE:
				if (queryValue != null && !(queryValue instanceof Date))
				{
					throw new MolgenisDataException(
							format("Attribute [%s] query value is of type [%s] instead of [%s]", attrName,
									queryValue.getClass().getSimpleName(), Date.class.getSimpleName()));
				}
				Date date = (Date) queryValue;
				return date != null ? new java.sql.Date(date.getTime()) : null;
			case DATE_TIME:
				if (queryValue != null && !(queryValue instanceof Date))
				{
					throw new MolgenisDataException(
							format("Attribute [%s] query value is of type [%s] instead of [%s]", attrName,
									queryValue.getClass().getSimpleName(), Date.class.getSimpleName()));
				}
				Date dateTime = (java.util.Date) queryValue;
				return dateTime != null ? new java.sql.Timestamp(dateTime.getTime()) : null;
			case DECIMAL:
				if (queryValue != null && !(queryValue instanceof Double))
				{
					throw new MolgenisDataException(
							format("Attribute [%s] query value is of type [%s] instead of [%s]", attrName,
									queryValue.getClass().getSimpleName(), Double.class.getSimpleName()));
				}
				return queryValue;
			case ENUM:
				// enum query values can be an enum or enum string
				if (queryValue != null)
				{
					if (queryValue instanceof String)
					{
						return queryValue;
					}
					else if (queryValue instanceof Enum<?>)
					{
						return queryValue.toString();
					}
					else
					{
						throw new MolgenisDataException(
								format("Attribute [%s] query value is of type [%s] instead of [%s] or [%s]", attrName,
										queryValue.getClass().getSimpleName(), String.class.getSimpleName(),
										Enum.class.getSimpleName()));
					}
				}
				else
				{
					return null;
				}
			case EMAIL:
			case HTML:
			case HYPERLINK:
			case SCRIPT:
			case STRING:
			case TEXT:
				if (queryValue != null && !(queryValue instanceof String))
				{
					throw new MolgenisDataException(
							format("Attribute [%s] query value is of type [%s] instead of [%s]", attrName,
									queryValue.getClass().getSimpleName(), String.class.getSimpleName()));
				}
				return queryValue;
			case INT:
				if (queryValue != null && !(queryValue instanceof Integer))
				{
					throw new MolgenisDataException(
							format("Attribute [%s] query value is of type [%s] instead of [%s]", attrName,
									queryValue.getClass().getSimpleName(), Integer.class.getSimpleName()));
				}
				return queryValue;
			case LONG:
				if (queryValue != null && !(queryValue instanceof Long))
				{
					throw new MolgenisDataException(
							format("Attribute [%s] query value is of type [%s] instead of [%s]", attrName,
									queryValue.getClass().getSimpleName(), Long.class.getSimpleName()));
				}
				return queryValue;
			case COMPOUND:
				throw new RuntimeException(format("Illegal attribute type [%s]", attrType.toString()));
			default:
				throw new RuntimeException(format("Unknown attribute type [%s]", attrType.toString()));
		}
	}
}
