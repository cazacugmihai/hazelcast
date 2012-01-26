/* 
 * Copyright (c) 2008-2010, Hazel Ltd. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hazelcast.impl.partition;

import com.hazelcast.impl.MemberImpl;
import com.hazelcast.nio.Address;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class HostAwareMemberGroupFactory implements MemberGroupFactory {

    public Collection<MemberGroup> createMemberGroups(Collection<MemberImpl> members) {
        Map<String, MemberGroup> groups = new HashMap<String, MemberGroup>();
        for (MemberImpl member : members) {
            Address address = member.getAddress();
            MemberGroup group = groups.get(address.getHost());
            if (group == null) {
                group = new DefaultMemberGroup();
                groups.put(address.getHost(), group);
            }
            group.addMember(member);
        }
        return groups.values();
    }
}
