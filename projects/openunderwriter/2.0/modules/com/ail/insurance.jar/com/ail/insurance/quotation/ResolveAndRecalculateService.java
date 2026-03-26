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
import com.ail.insurance.quotation.RefreshAssessmentSheetsService.RefreshAssessmentSheetsCommand;

/**
 * Service that recalculates a policy after referral resolution without re-running
 * the full risk assessment pipeline. This eliminates redundant re-execution of
 * the entire AssessRisk step when only resolution adjustments need to be applied.
 * <p>
 * The service accepts a policy in REFERRED status with MarkerResolutions already added.
 * Instead of re-running the full CalculatePremiumService (which re-runs AssessRisk from scratch):
 * <ol>
 *   <li>Resets status to APPLICATION</li>
 *   <li>Skips AssessRisk (assessment already done)</li>
 *   <li>Runs only RefreshAssessmentSheets to recalculate with resolution adjustments</li>
 *   <li>Re-evaluates referral/decline status</li>
 * </ol>
 */
@ServiceImplementation
public class ResolveAndRecalculateService extends Service<ResolveAndRecalculateService.ResolveAndRecalculateArgument> {
    private static final long serialVersionUID = 1L;

    @ServiceArgument
    public interface ResolveAndRecalculateArgument extends Argument {
        /**
         * Fetch the policy in REFERRED status to recalculate.
         * @return The referred policy.
         */
        Policy getPolicyArgRet();

        /**
         * Set the policy to recalculate.
         * @param policyArgRet The referred policy.
         */
        void setPolicyArgRet(Policy policyArgRet);
    }

    @ServiceCommand(defaultServiceClass = ResolveAndRecalculateService.class)
    public interface ResolveAndRecalculateCommand extends Command, ResolveAndRecalculateArgument {
    }

    @Override
    public void invoke() throws PreconditionException, BaseException {
        Policy policy = args.getPolicyArgRet();

        if (policy == null) {
            throw new PreconditionException("policy==null");
        }

        if (policy.getStatus() == null || !REFERRED.equals(policy.getStatus())) {
            throw new PreconditionException("policy.status==null || policy.status!=REFERRED");
        }

        if (policy.getAssessmentSheet() == null) {
            throw new PreconditionException("policy.assessmentSheet==null");
        }

        core = new CoreProxy(getConfigurationNamespace(), args.getCallersCore()).getCore();

        // Step 1: Reset status to APPLICATION so RefreshAssessmentSheets can process
        policy.setStatus(APPLICATION);

        // Step 2: Skip AssessRisk - assessment sheets already contain the risk data

        // Step 3: Run RefreshAssessmentSheets to recalculate with resolution adjustments
        RefreshAssessmentSheetsCommand rasc = core.newCommand(RefreshAssessmentSheetsCommand.class);
        rasc.setPolicyArgRet(policy);
        rasc.setOriginArg("ResolveAndRecalculate");
        rasc.invoke();
        policy = rasc.getPolicyArgRet();

        // Step 4: Re-evaluate referral/decline status
        if (policy.isMarkedForDecline()) {
            policy.setStatus(DECLINED);
        } else if (policy.isMarkedForRefer()) {
            policy.setStatus(REFERRED);
        } else {
            policy.setStatus(QUOTATION);
        }

        args.setPolicyArgRet(policy);
    }
}
