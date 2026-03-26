package com.ail.insurance.quotation;

import static com.ail.insurance.policy.PolicyStatus.APPLICATION;
import static com.ail.insurance.policy.PolicyStatus.DECLINED;
import static com.ail.insurance.policy.PolicyStatus.QUOTATION;
import static com.ail.insurance.policy.PolicyStatus.REFERRED;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.ail.core.BaseException;
import com.ail.core.Core;
import com.ail.core.PreconditionException;
import com.ail.insurance.policy.AssessmentSheet;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.quotation.RefreshAssessmentSheetsService.RefreshAssessmentSheetsCommand;
import com.ail.insurance.quotation.ResolveAndRecalculateService.ResolveAndRecalculateArgument;

public class ResolveAndRecalculateServiceTest {

    private ResolveAndRecalculateService sut;
    private Core mockCore;
    private ResolveAndRecalculateArgument mockArgs;
    private Policy mockPolicy;
    private RefreshAssessmentSheetsCommand mockRefreshCommand;
    private AssessmentSheet mockAssessmentSheet;

    @Before
    public void setup() throws BaseException {
        mockCore = mock(Core.class);
        mockArgs = mock(ResolveAndRecalculateArgument.class);
        mockPolicy = mock(Policy.class);
        mockRefreshCommand = mock(RefreshAssessmentSheetsCommand.class);
        mockAssessmentSheet = mock(AssessmentSheet.class);

        sut = spy(new ResolveAndRecalculateService());
        sut.setCore(mockCore);
        sut.setArgs(mockArgs);

        when(sut.getCore()).thenReturn(mockCore);
        when(mockArgs.getPolicyArgRet()).thenReturn(mockPolicy);
        when(mockArgs.getCallersCore()).thenReturn(mockCore);
        when(mockPolicy.getProductTypeId()).thenReturn("TestProduct");
        when(mockPolicy.getStatus()).thenReturn(REFERRED);
        when(mockPolicy.getAssessmentSheet()).thenReturn(mockAssessmentSheet);
        when(mockPolicy.isMarkedForDecline()).thenReturn(false);
        when(mockPolicy.isMarkedForRefer()).thenReturn(false);

        when(mockCore.newCommand(RefreshAssessmentSheetsCommand.class)).thenReturn(mockRefreshCommand);
        when(mockRefreshCommand.getPolicyArgRet()).thenReturn(mockPolicy);
    }

    @Test(expected = PreconditionException.class)
    public void testNullPolicy() throws BaseException {
        when(mockArgs.getPolicyArgRet()).thenReturn(null);
        sut.invoke();
    }

    @Test(expected = PreconditionException.class)
    public void testNullStatus() throws BaseException {
        when(mockPolicy.getStatus()).thenReturn(null);
        sut.invoke();
    }

    @Test(expected = PreconditionException.class)
    public void testNonReferredStatus() throws BaseException {
        when(mockPolicy.getStatus()).thenReturn(APPLICATION);
        sut.invoke();
    }

    @Test(expected = PreconditionException.class)
    public void testNullAssessmentSheet() throws BaseException {
        when(mockPolicy.getAssessmentSheet()).thenReturn(null);
        sut.invoke();
    }

    @Test
    public void testHappyPathResolvesToQuotation() throws BaseException {
        sut.invoke();

        // Verify status was first reset to APPLICATION
        verify(mockPolicy).setStatus(eq(APPLICATION));
        // Verify RefreshAssessmentSheets was invoked
        verify(mockRefreshCommand).invoke();
        // Verify final status is QUOTATION (not declined, not referred)
        verify(mockPolicy).setStatus(eq(QUOTATION));
        // Verify result is set back
        verify(mockArgs).setPolicyArgRet(eq(mockPolicy));
    }

    @Test
    public void testResolvesToDeclined() throws BaseException {
        when(mockPolicy.isMarkedForDecline()).thenReturn(true);
        sut.invoke();
        verify(mockPolicy).setStatus(eq(DECLINED));
    }

    @Test
    public void testResolvesToReferredAgain() throws BaseException {
        when(mockPolicy.isMarkedForRefer()).thenReturn(true);
        sut.invoke();
        verify(mockPolicy).setStatus(eq(REFERRED));
    }

    @Test
    public void testRefreshAssessmentSheetsCalledWithCorrectOrigin() throws BaseException {
        sut.invoke();
        verify(mockRefreshCommand).setOriginArg(eq("ResolveAndRecalculate"));
    }
}
