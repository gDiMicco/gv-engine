/*******************************************************************************
 * Copyright (c) 2009, 2016 GreenVulcano ESB Open Source Project.
 * All rights reserved.
 *
 * This file is part of GreenVulcano ESB.
 *
 * GreenVulcano ESB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GreenVulcano ESB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GreenVulcano ESB. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package it.greenvulcano.gvesb.iam.repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import it.greenvulcano.gvesb.iam.domain.User;

/**
 * Business interface to deal with {@link User} entity
 * 
 */
public interface UserRepository extends Repository<User, Long> {
	
	enum Parameter {
		username, fullname, email, expired, creationTime, updateTime, passwordTime, enabled, role;
		
		public static Parameter get(String v) {
			try {
				return valueOf(v);
			} catch (Exception e) {
				return null;
			}
		}
	}
	
	enum Order {desc, asc;
	
		public static Order get(String v) {
			try {
				return valueOf(v);
			} catch (Exception e) {
				return null;
			}
		}
	}
	
	Optional<User> get(String username);
			
	Set<User> find(Map<Parameter, Object> parameters, LinkedHashMap<Parameter, Order> order, int firstResult, int maxResult);
	
	int count(Map<Parameter, Object> parameters);

}
