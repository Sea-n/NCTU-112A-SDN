/*
 * Copyright 2020-present Open Networking Foundation
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
package nctu.winlab.unicastdhcp;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.ConnectPoint;

public class SrvConfig extends Config<ApplicationId> {

  public static final String SRV_LOC = "serverLocation";
  public static final String CLI_LOC = "clientLocation";

  @Override
  public boolean isValid() {
    return hasOnlyFields(SRV_LOC, CLI_LOC);
  }

  public ConnectPoint srv() {
    return ConnectPoint.deviceConnectPoint(get(SRV_LOC, null));
  }

  public ConnectPoint cli() {
    return ConnectPoint.deviceConnectPoint(get(CLI_LOC, null));
  }
}
