package org.molgenis.data.annotation.core.exception;

import org.molgenis.data.CodedRuntimeException;

public class UnresolvedAnnotatorDependencyException extends CodedRuntimeException
{
	private static final String ERROR_CODE = "AN07";
	private String annotatorName;

	public UnresolvedAnnotatorDependencyException(String annotatorName)
	{
		super(ERROR_CODE);
		this.annotatorName = annotatorName;
	}
}
