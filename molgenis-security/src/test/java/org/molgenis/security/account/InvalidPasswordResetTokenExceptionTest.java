package org.molgenis.security.account;

import static org.mockito.Mockito.mock;

import org.molgenis.data.security.auth.PasswordResetToken;
import org.molgenis.util.exception.ExceptionMessageTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class InvalidPasswordResetTokenExceptionTest extends ExceptionMessageTest {
  @BeforeMethod
  public void setUp() {
    messageSource.addMolgenisNamespaces("security");
  }

  @Test(dataProvider = "languageMessageProvider")
  @Override
  public void testGetLocalizedMessage(String lang, String message) {
    PasswordResetToken passwordResetToken = mock(PasswordResetToken.class);
    assertExceptionMessageEquals(
        new InvalidPasswordResetTokenException(passwordResetToken), lang, message);
  }

  @DataProvider(name = "languageMessageProvider")
  @Override
  public Object[][] languageMessageProvider() {
    Object[] enParams = {"en", "The password reset link is invalid."};
    Object[] nlParams = {"nl", "De wachtwoord reset link is ongeldig."};
    return new Object[][] {enParams, nlParams};
  }
}