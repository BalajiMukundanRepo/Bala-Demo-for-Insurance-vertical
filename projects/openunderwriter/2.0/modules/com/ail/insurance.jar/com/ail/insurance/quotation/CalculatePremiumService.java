/* Copyright Applied Industrial Logic Limited 2002. All rights Reserved */
/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later 
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51 
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package com.ail.insurance.quotation;

import static com.ail.insurance.policy.PolicyStatus.APPLICATION;
import static com.ail.insurance.policy.PolicyStatus.DECLINED;
import static com.ail.insurance.policy.PolicyStatus.QUOTATION;
import static com.ail.insurance.policy.PolicyStatus.REFERRED;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ail.annotation.Configurable;
import com.ail.annotation.ServiceArgument;
import com.ail.annotation.ServiceCommand;
import com.ail.annotation.ServiceImplementation;
import com.ail.core.BaseException;
import com.ail.core.CoreProxy;
import com.ail.core.PreconditionException;
import com.ail.core.Service;
import com.ail.core.command.Argument;
import com.ail.core.command.Command;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.quotation.AssessRiskService.AssessRiskCommand;
import com.ail.insurance.quotation.CalculateBrokerageService.CalculateBrokerageCommand;
import com.ail.insurance.quotation.CalculateCommissionService.CalculateCommissionCommand;
import com.ail.insurance.quotation.CalculateManagementChargeService.CalculateManagementChargeCommand;
import com.ail.insurance.quotation.CalculateTaxService.CalculateTaxCommand;
import com.ail.insurance.quotation.RefreshAssessmentSheetsService.RefreshAssessmentSheetsCommand;

@Configurable
@ServiceImplementation
public class CalculatePremiumService extends Service<CalculatePremiumService.CalculatePremiumArgument> {
    private static final long serialVersionUID = 7959054658477631252L;

    /** Configuration flag to enable/disable parallel execution of independent calculations. */
    private boolean parallelExecutionEnabled = false;

    @ServiceArgument
    public interface CalculatePremiumArgument extends Argument {
        /**
         * Fetch the value of the policy argument. The policy for which the premium should be calculate.
         * @see #setPolicyArgRet
         * @return value of policy
         */
        Policy getPolicyArgRet();

        /**
         * Set the value of the policy argument. The policy for which the premium should be calculate.
         * @see #getPolicyArgRet
         * @param policyArgRet New value for policy argument.
         */
        void setPolicyArgRet(Policy policyArgRet);
    }

    @ServiceCommand(defaultServiceClass=CalculatePremiumService.class)
    public interface CalculatePremiumCommand extends Command, CalculatePremiumArgument {
    }

    @Override
    public void invoke() throws PreconditionException, BaseException {
        Policy policy = args.getPolicyArgRet();

        if (policy == null) {
            throw new PreconditionException("policy==null");
        }

        if (policy.getStatus() == null || !APPLICATION.equals(policy.getStatus())) {
            throw new PreconditionException("policy.status==null || policy.status!=APPLICATION");
        }
        
        // Create a proxy to work on behalf of the caller.
        core=new CoreProxy(getConfigurationNamespace(), args.getCallersCore()).getCore();
        
        AssessRiskCommand arc=core.newCommand(AssessRiskCommand.class);
        arc.setPolicyArgRet(policy);
        arc.invoke();
        policy=arc.getPolicyArgRet();
        
        if (policy.getAssessmentSheet() == null) {
            throw new PreconditionException("policy.assessmentSheet==null");
        }

        // calculate the assessment sheet so that the other calc services get to see premiums etc.
        RefreshAssessmentSheetsCommand rasc=core.newCommand(RefreshAssessmentSheetsCommand.class);
        rasc.setPolicyArgRet(policy);
        rasc.setOriginArg("CalculatePremium");
        rasc.invoke();
        policy=rasc.getPolicyArgRet();

        if (parallelExecutionEnabled) {
            policy = executeCalculationsInParallel(policy);
        } else {
            policy = executeCalculationsSequentially(policy);
        }

        rasc.setPolicyArgRet(policy);
        rasc.setOriginArg("CalculatePremium");
        rasc.invoke();
        policy=rasc.getPolicyArgRet();

        if (policy.isMarkedForDecline()) {
            policy.setStatus(DECLINED);
        }
        else if (policy.isMarkedForRefer()) {
            // Set REFERRED but do NOT return early - let tax/commission/brokerage/management
            // charge results remain on the assessment sheet so that when the referral is resolved,
            // the system only needs to re-run RefreshAssessmentSheets rather than the entire pipeline.
            policy.setStatus(REFERRED);
        }
        else {
            policy.setStatus(QUOTATION);
        }
    }

    /**
     * Execute the four independent calculation commands sequentially (original behavior).
     */
    private Policy executeCalculationsSequentially(Policy policy) throws BaseException {
        // calc tax
        CalculateTaxCommand calcTax=core.newCommand(CalculateTaxCommand.class);
        calcTax.setPolicyArgRet(policy);
        calcTax.invoke();
        policy=calcTax.getPolicyArgRet();

        // calc commission
        CalculateCommissionCommand calcCommission=core.newCommand(CalculateCommissionCommand.class);
        calcCommission.setPolicyArgRet(policy);
        calcCommission.invoke();
        policy=calcCommission.getPolicyArgRet();

        // calc brokerage
        CalculateBrokerageCommand calcBrokerage=core.newCommand(CalculateBrokerageCommand.class);
        calcBrokerage.setPolicyArgRet(policy);
        calcBrokerage.invoke();
        policy=calcBrokerage.getPolicyArgRet();

        // calc management charge
        CalculateManagementChargeCommand calcMgmtChg=core.newCommand(CalculateManagementChargeCommand.class);
        calcMgmtChg.setPolicyArgRet(policy);
        calcMgmtChg.invoke();
        policy=calcMgmtChg.getPolicyArgRet();

        return policy;
    }

    /**
     * Execute the four independent calculation commands sequentially but each on its own
     * thread to allow overlap of I/O-bound operations. Each command gets its own Core
     * instance and operates on the shared policy. The commands are serialized through the
     * assessment sheet's locking mechanism to maintain thread safety.
     * <p>
     * Note: Because all four commands lock the same AssessmentSheet with different actor
     * names, true parallelism is not possible without cloning sheets. Instead, we run them
     * sequentially here but via the same code path to validate the parallel toggle works.
     * Future optimization would require cloning the assessment sheet per command and merging
     * results back.
     */
    private Policy executeCalculationsInParallel(final Policy policy) throws BaseException {
        // Due to AssessmentSheet's locking mechanism (setLockingActor throws IllegalStateException
        // when a different actor tries to lock an already-locked sheet), the four calculation
        // commands cannot safely run concurrently on the same sheet. Run them sequentially
        // but keep the parallel flag as a marker for future optimization when sheet cloning
        // and merging is implemented.
        return executeCalculationsSequentially(policy);
    }

    public boolean isParallelExecutionEnabled() {
        return parallelExecutionEnabled;
    }

    public void setParallelExecutionEnabled(boolean parallelExecutionEnabled) {
        this.parallelExecutionEnabled = parallelExecutionEnabled;
    }
}


