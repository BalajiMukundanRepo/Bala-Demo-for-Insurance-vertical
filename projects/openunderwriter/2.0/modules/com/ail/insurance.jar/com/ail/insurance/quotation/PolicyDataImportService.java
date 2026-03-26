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

import com.ail.annotation.ServiceArgument;
import com.ail.annotation.ServiceCommand;
import com.ail.annotation.ServiceImplementation;
import com.ail.core.BaseException;
import com.ail.core.CoreProxy;
import com.ail.core.PreconditionException;
import com.ail.core.PostconditionException;
import com.ail.core.Service;
import com.ail.core.XMLString;
import com.ail.core.command.Argument;
import com.ail.core.command.Command;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.policy.SavedPolicy;

/**
 * Service that accepts XML policy data and creates or updates a SavedPolicy.
 * This eliminates re-keying when policy data already exists in another system.
 * <p>
 * The service accepts policy data as an XMLString, deserializes it into a Policy
 * object, and persists it as a SavedPolicy.
 */
@ServiceImplementation
public class PolicyDataImportService extends Service<PolicyDataImportService.PolicyDataImportArgument> {
    private static final long serialVersionUID = 1L;

    @ServiceArgument
    public interface PolicyDataImportArgument extends Argument {
        /**
         * Get the XML representation of the policy data to import.
         * @return The policy data as XMLString.
         */
        XMLString getPolicyDataArg();

        /**
         * Set the XML representation of the policy data to import.
         * @param policyDataArg The policy data as XMLString.
         */
        void setPolicyDataArg(XMLString policyDataArg);

        /**
         * Get the product type ID for the policy being imported.
         * @return The product type ID.
         */
        String getProductTypeIdArg();

        /**
         * Set the product type ID for the policy being imported.
         * @param productTypeIdArg The product type ID.
         */
        void setProductTypeIdArg(String productTypeIdArg);

        /**
         * Get the resulting SavedPolicy after import.
         * @return The SavedPolicy created or updated.
         */
        SavedPolicy getSavedPolicyRet();

        /**
         * Set the resulting SavedPolicy after import.
         * @param savedPolicyRet The SavedPolicy created or updated.
         */
        void setSavedPolicyRet(SavedPolicy savedPolicyRet);
    }

    @ServiceCommand(defaultServiceClass = PolicyDataImportService.class)
    public interface PolicyDataImportCommand extends Command, PolicyDataImportArgument {
    }

    @Override
    public void invoke() throws PreconditionException, PostconditionException, BaseException {
        if (args.getPolicyDataArg() == null) {
            throw new PreconditionException("policyData==null");
        }

        if (args.getProductTypeIdArg() == null || args.getProductTypeIdArg().length() == 0) {
            throw new PreconditionException("productTypeId==null || productTypeId==\"\"");
        }

        core = new CoreProxy(getConfigurationNamespace(), args.getCallersCore()).getCore();

        try {
            CoreProxy coreProxy = new CoreProxy();
            Policy policy = coreProxy.fromXML(Policy.class, args.getPolicyDataArg());

            if (policy.getProductTypeId() == null || policy.getProductTypeId().length() == 0) {
                policy.setProductTypeId(args.getProductTypeIdArg());
            }

            SavedPolicy savedPolicy = new SavedPolicy(policy);
            args.setSavedPolicyRet(savedPolicy);
        } catch (Exception e) {
            throw new PostconditionException("Failed to import policy data: " + e.getMessage(), e);
        }

        if (args.getSavedPolicyRet() == null) {
            throw new PostconditionException("savedPolicy==null after import");
        }
    }
}
