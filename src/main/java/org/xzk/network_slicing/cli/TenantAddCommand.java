/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.TenantId;
import org.onosproject.incubator.net.virtual.VirtualNetworkAdminService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(scope = "onos", name = "ns-add-tenant",
        description = "Creates and registers a new tenant")
public class TenantAddCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "tenantId", description = "Tenant ID",
            required = true, multiValued = false)
    String tenantId = null;

    @Override
    protected void execute() {
        Pattern validTenantId = Pattern.compile("^[a-zA-Z0-9_]*$");
        Matcher validTenantIdChecker = validTenantId.matcher(tenantId);

        if (validTenantIdChecker.find()) {  // Valid TenantID
            VirtualNetworkAdminService virtualNetworkAdminService = getService(VirtualNetworkAdminService.class);
            if (!virtualNetworkAdminService.getTenantIds().contains(TenantId.tenantId(tenantId))) {   // New user
                virtualNetworkAdminService.registerTenantId(TenantId.tenantId(tenantId));
                print("Tenant " + tenantId + " registered successfully!");
            } else {    // User already exists
                error("Tenant " + tenantId + " already exists!");
            }
        } else {    // Invalid TenantID
            error("Invalid Tenant ID input. Tenant ID can only contain alphanumeric characters and underscore only");
        }
    }
}
