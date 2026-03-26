package com.ail.insurance.quotation;

import static com.ail.insurance.policy.PolicyStatus.APPLICATION;
import static com.ail.insurance.policy.PolicyStatus.DECLINED;
import static com.ail.insurance.policy.PolicyStatus.ON_RISK;
import static com.ail.insurance.policy.PolicyStatus.QUOTATION;
import static com.ail.insurance.policy.PolicyStatus.REFERRED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
import com.ail.insurance.policy.PolicyStatus;
import com.ail.insurance.quotation.AssessRiskService.AssessRiskCommand;
import com.ail.insurance.quotation.CalculateBrokerageService.CalculateBrokerageCommand;
import com.ail.insurance.quotation.CalculateCommissionService.CalculateCommissionCommand;
import com.ail.insurance.quotation.CalculateManagementChargeService.CalculateManagementChargeCommand;
import com.ail.insurance.quotation.AutoResolveReferralService.AutoResolveReferralCommand;
import com.ail.insurance.quotation.CalculatePremiumService.CalculatePremiumArgument;
import com.ail.insurance.quotation.CalculateTaxService.CalculateTaxCommand;
import com.ail.insurance.quotation.RefreshAssessmentSheetsService.RefreshAssessmentSheetsCommand;

public class CalculatePremiumServiceTest {

    private CalculatePremiumService sut;
    private Core mockCore;
    private CalculatePremiumArgument mockArgs;
    private Policy mockPolicy;
    private AssessRiskCommand mockAssessRiskCommand;
    private RefreshAssessmentSheetsCommand mockRefreshCommand;
    private CalculateTaxCommand mockCalcTaxCommand;
    private CalculateCommissionCommand mockCalcCommissionCommand;
    private CalculateBrokerageCommand mockCalcBrokerageCommand;
    private CalculateManagementChargeCommand mockCalcMgmtChargeCommand;
    private AssessmentSheet mockAssessmentSheet;

    @Before
    public void setup() throws BaseException {
        mockCore = mock(Core.class);
        mockArgs = mock(CalculatePremiumArgument.class);
        mockPolicy = mock(Policy.class);
        mockAssessRiskCommand = mock(AssessRiskCommand.class);
        mockRefreshCommand = mock(RefreshAssessmentSheetsCommand.class);
        mockCalcTaxCommand = mock(CalculateTaxCommand.class);
        mockCalcCommissionCommand = mock(CalculateCommissionCommand.class);
        mockCalcBrokerageCommand = mock(CalculateBrokerageCommand.class);
        mockCalcMgmtChargeCommand = mock(CalculateManagementChargeCommand.class);
        mockAssessmentSheet = mock(AssessmentSheet.class);

        sut = spy(new CalculatePremiumService());
        sut.setCore(mockCore);
        sut.setArgs(mockArgs);

        when(sut.getCore()).thenReturn(mockCore);
        when(mockArgs.getPolicyArgRet()).thenReturn(mockPolicy);
        when(mockArgs.getCallersCore()).thenReturn(mockCore);
        when(mockPolicy.getProductTypeId()).thenReturn("TestProduct");
        when(mockPolicy.getStatus()).thenReturn(APPLICATION);
        when(mockPolicy.getAssessmentSheet()).thenReturn(mockAssessmentSheet);
        when(mockPolicy.isMarkedForDecline()).thenReturn(false);
        when(mockPolicy.isMarkedForRefer()).thenReturn(false);

        when(mockCore.newCommand(AssessRiskCommand.class)).thenReturn(mockAssessRiskCommand);
        when(mockAssessRiskCommand.getPolicyArgRet()).thenReturn(mockPolicy);

        when(mockCore.newCommand(RefreshAssessmentSheetsCommand.class)).thenReturn(mockRefreshCommand);
        when(mockRefreshCommand.getPolicyArgRet()).thenReturn(mockPolicy);

        when(mockCore.newCommand(CalculateTaxCommand.class)).thenReturn(mockCalcTaxCommand);
        when(mockCalcTaxCommand.getPolicyArgRet()).thenReturn(mockPolicy);

        when(mockCore.newCommand(CalculateCommissionCommand.class)).thenReturn(mockCalcCommissionCommand);
        when(mockCalcCommissionCommand.getPolicyArgRet()).thenReturn(mockPolicy);

        when(mockCore.newCommand(CalculateBrokerageCommand.class)).thenReturn(mockCalcBrokerageCommand);
        when(mockCalcBrokerageCommand.getPolicyArgRet()).thenReturn(mockPolicy);

        when(mockCore.newCommand(CalculateManagementChargeCommand.class)).thenReturn(mockCalcMgmtChargeCommand);
        when(mockCalcMgmtChargeCommand.getPolicyArgRet()).thenReturn(mockPolicy);
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
    public void testWrongStatus() throws BaseException {
        when(mockPolicy.getStatus()).thenReturn(QUOTATION);
        sut.invoke();
    }

    @Test
    public void testInvalidStatuses() throws BaseException {
        PolicyStatus[] invalidStatuses = {DECLINED, ON_RISK, QUOTATION, REFERRED};

        for (PolicyStatus status : invalidStatuses) {
            when(mockPolicy.getStatus()).thenReturn(status);
            try {
                sut.invoke();
            } catch (PreconditionException e) {
                // expected
            }
        }
    }

    @Test
    public void testHappyPathSequential() throws BaseException {
        sut.invoke();
        verify(mockPolicy).setStatus(eq(QUOTATION));
    }

    @Test
    public void testDeclinedStatus() throws BaseException {
        when(mockPolicy.isMarkedForDecline()).thenReturn(true);
        sut.invoke();
        verify(mockPolicy).setStatus(eq(DECLINED));
    }

    @Test
    public void testReferredStatusPreservesCalculations() throws BaseException {
        // This tests Task 4b: when marked for refer, status is set to REFERRED
        // but calculations are NOT discarded (method does not return early)
        when(mockPolicy.isMarkedForRefer()).thenReturn(true);
        sut.invoke();
        verify(mockPolicy).setStatus(eq(REFERRED));

        // Verify that all calculation commands were still invoked
        verify(mockCalcTaxCommand).invoke();
        verify(mockCalcCommissionCommand).invoke();
        verify(mockCalcBrokerageCommand).invoke();
        verify(mockCalcMgmtChargeCommand).invoke();
    }

    @Test
    public void testParallelExecutionDefault() {
        assertFalse("Parallel execution should be disabled by default",
                sut.isParallelExecutionEnabled());
    }

    @Test
    public void testParallelExecutionToggle() {
        sut.setParallelExecutionEnabled(true);
        assertTrue(sut.isParallelExecutionEnabled());
        sut.setParallelExecutionEnabled(false);
        assertFalse(sut.isParallelExecutionEnabled());
    }

    @Test
    public void testParallelExecution() throws BaseException {
        sut.setParallelExecutionEnabled(true);
        sut.invoke();
        verify(mockPolicy).setStatus(eq(QUOTATION));
        // Verify all four calculations ran
        verify(mockCalcTaxCommand).invoke();
        verify(mockCalcCommissionCommand).invoke();
        verify(mockCalcBrokerageCommand).invoke();
        verify(mockCalcMgmtChargeCommand).invoke();
    }

    @Test
    public void testParallelExecutionWithReferral() throws BaseException {
        sut.setParallelExecutionEnabled(true);
        when(mockPolicy.isMarkedForRefer()).thenReturn(true);
        sut.invoke();
        verify(mockPolicy).setStatus(eq(REFERRED));
        // Verify all four calculations still ran even with referral
        verify(mockCalcTaxCommand).invoke();
        verify(mockCalcCommissionCommand).invoke();
        verify(mockCalcBrokerageCommand).invoke();
        verify(mockCalcMgmtChargeCommand).invoke();
    }

    @Test
    public void testAutoResolveReferralInPipeline() throws BaseException {
        // Policy is marked for refer initially, then not after auto-resolve
        when(mockPolicy.isMarkedForRefer()).thenReturn(true).thenReturn(false);

        AutoResolveReferralCommand mockAutoResolve = mock(AutoResolveReferralCommand.class);
        when(mockCore.newCommand(AutoResolveReferralCommand.class)).thenReturn(mockAutoResolve);
        when(mockAutoResolve.getPolicyArgRet()).thenReturn(mockPolicy);

        sut.invoke();

        // Auto-resolve was invoked
        verify(mockAutoResolve).setPolicyArgRet(mockPolicy);
        verify(mockAutoResolve).setTolerancePercentArg(10.0);
        verify(mockAutoResolve).invoke();

        // After auto-resolve cleared all referrals, status should be QUOTATION
        verify(mockPolicy).setStatus(eq(QUOTATION));
    }

    @Test
    public void testAutoResolveReferralPartialResolution() throws BaseException {
        // Policy remains marked for refer even after auto-resolve
        when(mockPolicy.isMarkedForRefer()).thenReturn(true);

        AutoResolveReferralCommand mockAutoResolve = mock(AutoResolveReferralCommand.class);
        when(mockCore.newCommand(AutoResolveReferralCommand.class)).thenReturn(mockAutoResolve);
        when(mockAutoResolve.getPolicyArgRet()).thenReturn(mockPolicy);

        sut.invoke();

        // Auto-resolve was invoked but some referrals remain
        verify(mockAutoResolve).invoke();

        // Status should still be REFERRED since not all referrals were resolved
        verify(mockPolicy).setStatus(eq(REFERRED));
    }
}
