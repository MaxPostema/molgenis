package org.molgenis.bbmri.directory.model;

import com.google.auto.value.AutoValue;
import org.molgenis.gson.AutoGson;

import java.util.List;

@AutoValue
@AutoGson(autoValueClass = AutoValue_NegotiatorQuery.class)
public abstract class NegotiatorQuery
{
	public abstract String getURL();

	public abstract List<Collection> getCollections();

	public abstract String getHumanReadable();

	public abstract String getnToken();

	public static NegotiatorQuery createQuery(String url, List<Collection> collections, String humanReadable, String nToken)
	{
		return new AutoValue_NegotiatorQuery(url, collections, humanReadable, nToken);
	}
}