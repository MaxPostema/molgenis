package org.molgenis.api.convert;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import org.molgenis.util.exception.CodedRuntimeException;

public class SelectionParseException extends CodedRuntimeException {
  private static final String ERROR_CODE = "API02";
  private final ParseException parseException;

  public SelectionParseException(ParseException parseException) {
    super(ERROR_CODE);
    this.parseException = requireNonNull(parseException);
  }

  @Override
  public String getMessage() {
    return format("parseException: %s", parseException.getMessage());
  }

  @Override
  protected Object[] getLocalizedMessageArguments() {
    return new Object[] {
      parseException.currentToken.image,
      parseException.currentToken.beginLine,
      parseException.currentToken.beginColumn
    };
  }
}
