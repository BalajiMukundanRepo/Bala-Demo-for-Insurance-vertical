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
import com.ail.core.Service;
import com.ail.core.command.Argument;
import com.ail.core.command.Command;
import com.ail.insurance.policy.Policy;

/**
 * Service to auto-populate policy attributes (proposer details, asset details) from
 * external data sources. This reduces manual data collection and re-keying by
 * pre-filling fields that would otherwise require manual entry.
 * <p>
 * The service accepts a Policy and a data source identifier, and populates policy
 * attributes from the specified external data source using the WebServiceAccessor pattern.
 */
@ServiceImplementation
public class DataPrefillService extends Service<DataPrefillService.DataPrefillArgument> {
    private static final long serialVersionUID = 1L;

    @ServiceArgument
    public interface DataPrefillArgument extends Argument {
        /**
         * Fetch the policy to be pre-filled with data.
         * @return The policy to pre-fill.
         */
        Policy getPolicyArgRet();

        /**
         * Set the policy to be pre-filled with data.
         * @param policyArgRet The policy to pre-fill.
         */
        void setPolicyArgRet(Policy policyArgRet);

        /**
         * Get the data source identifier indicating where to fetch pre-fill data from.
         * @return The data source identifier.
         */
        String getDataSourceArg();

        /**
         * Set the data source identifier indicating where to fetch pre-fill data from.
         * @param dataSourceArg The data source identifier.
         */
        void setDataSourceArg(String dataSourceArg);
    }

    @ServiceCommand(defaultServiceClass = DataPrefillService.class)
    public interface DataPrefillCommand extends Command, DataPrefillArgument {
    }

    @Override
    public void invoke() throws PreconditionException, BaseException {
        Policy policy = args.getPolicyArgRet();

        if (policy == null) {
            throw new PreconditionException("policy==null");
        }

        if (args.getDataSourceArg() == null || args.getDataSourceArg().length() == 0) {
            throw new PreconditionException("dataSource==null || dataSource==\"\"");
        }

        core = new CoreProxy(getConfigurationNamespace(), args.getCallersCore()).getCore();

        String dataSource = args.getDataSourceArg();

        // Attempt to invoke a product-specific prefill command if configured.
        // The command name follows the convention: DataPrefill/<dataSource>
        String commandName = "DataPrefill/" + dataSource;
        DataPrefillCommand prefillCmd;
        try {
            prefillCmd = core.newCommand(commandName, DataPrefillCommand.class);
        } catch (Exception e) {
            // No product-specific prefill command is configured — log and continue.
            // The policy will proceed without pre-filled data.
            core.logInfo("No DataPrefill command configured for source '" + dataSource + "': " + e.getMessage());
            args.setPolicyArgRet(policy);
            return;
        }

        // Command exists — invoke it. Let execution failures propagate to the caller.
        prefillCmd.setPolicyArgRet(policy);
        prefillCmd.setDataSourceArg(dataSource);
        prefillCmd.invoke();
        policy = prefillCmd.getPolicyArgRet();

        args.setPolicyArgRet(policy);
    }
}
