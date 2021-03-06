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
package it.greenvulcano.gvesb.iam.service.internal;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.greenvulcano.gvesb.iam.domain.Role;
import it.greenvulcano.gvesb.iam.domain.User;
import it.greenvulcano.gvesb.iam.domain.UserInfo;
import it.greenvulcano.gvesb.iam.exception.InvalidPasswordException;
import it.greenvulcano.gvesb.iam.exception.InvalidRoleException;
import it.greenvulcano.gvesb.iam.exception.InvalidUsernameException;
import it.greenvulcano.gvesb.iam.exception.PasswordMissmatchException;
import it.greenvulcano.gvesb.iam.exception.UserExistException;
import it.greenvulcano.gvesb.iam.exception.UserExpiredException;
import it.greenvulcano.gvesb.iam.exception.UserNotFoundException;
import it.greenvulcano.gvesb.iam.jaas.GVBackingEngine;
import it.greenvulcano.gvesb.iam.jaas.GVBackingEngineFactory;
import it.greenvulcano.gvesb.iam.repository.RoleRepository;
import it.greenvulcano.gvesb.iam.repository.UserRepository;
import it.greenvulcano.gvesb.iam.repository.UserRepository.Order;
import it.greenvulcano.gvesb.iam.repository.UserRepository.Parameter;
import it.greenvulcano.gvesb.iam.service.SearchCriteria;
import it.greenvulcano.gvesb.iam.service.SearchResult;
import it.greenvulcano.gvesb.iam.service.UsersManager;

public class GVUsersManager implements UsersManager {

	private GVBackingEngine jaasEngine;
	private UserRepository userRepository;
	private RoleRepository roleRepository;
	
	public void setUserRepository(UserRepository userRepository) {
		this.userRepository = userRepository;
	}
	
	public void setRoleRepository(RoleRepository roleRepository) {
		this.roleRepository = roleRepository;
	}
	
	public void setJaasEngineFactory(GVBackingEngineFactory gvBackingEngineFactory) {
		this.jaasEngine = (GVBackingEngine) gvBackingEngineFactory.build();
	}
	
	@Override
	public User createUser(String username, String password)
			throws InvalidUsernameException, InvalidPasswordException, UserExistException {		
		jaasEngine.addUser(username, password);
		
		return userRepository.get(username).get();
	}
	
	@Override
	public Role createRole(String name, String description) throws InvalidRoleException {
		if (!name.matches(Role.ROLE_PATTERN)) throw new InvalidRoleException(name);
		
		Role role = new Role(name, description);
		roleRepository.add(role);
		return role;
	}
	

	@Override
	public void updateUser(String username, UserInfo userInfo, Set<Role> grantedRoles, boolean enabled, boolean expired) throws UserNotFoundException, InvalidRoleException {
		User user = userRepository.get(username).orElseThrow(()->new UserNotFoundException(username));
		user.setUserInfo(userInfo);
		user.setEnabled(enabled);
		user.setExpired(expired);
		user.getRoles().clear();
		if (grantedRoles!=null) {
			
			Predicate<Role> roleIsValid = role-> Optional.ofNullable(role.getName()).orElse("").matches(Role.ROLE_PATTERN);
			
			Optional<Role> notValidRole = grantedRoles.stream().filter(roleIsValid.negate()).findAny();
			if (notValidRole.isPresent()){
				throw new InvalidRoleException(notValidRole.get().getName());
			}
			
			for (Role r : grantedRoles) {
				user.getRoles().add(roleRepository.get(r.getName()).orElse(r));
			}
			
		}
		userRepository.add(user);
	}

	@Override
	public User getUser(Long id) throws UserNotFoundException {
		User user = userRepository.get(id).orElseThrow(()->new UserNotFoundException(id.toString()));
		return user;
	}
	
	@Override
	public User getUser(String username) throws UserNotFoundException {
		User user = userRepository.get(username).orElseThrow(()->new UserNotFoundException(username));
		return user;
	}

	@Override
	public void deleteUser(String username) {
		userRepository.get(username).ifPresent(userRepository::remove);
	}

	@Override
	public User resetUserPassword(String username, String defaultPassword) throws UserNotFoundException, InvalidPasswordException {
		User user = userRepository.get(username).orElseThrow(()->new UserNotFoundException(username));
	
		if (!Objects.requireNonNull(defaultPassword, "A default password is required").matches(User.PASSWORD_PATTERN)) throw new InvalidPasswordException(defaultPassword);
		
		user.setPassword(jaasEngine.getEncryptedPassword(defaultPassword));
		user.setPasswordTime(new Date());
		user.setExpired(true);
		userRepository.add(user);
		return user;

	}

	@Override
	public User changeUserPassword(String username, String oldPassword, String newPassword)
			throws UserNotFoundException, PasswordMissmatchException, InvalidPasswordException {
		
		User user = userRepository.get(username).orElseThrow(()->new UserNotFoundException(username));
		if (!newPassword.matches(User.PASSWORD_PATTERN)) throw new InvalidPasswordException(newPassword);
				
		user.setUsername(username);
		user.setPassword(jaasEngine.getEncryptedPassword(newPassword));
		user.setPasswordTime(new Date());
		user.setExpired(false);
		
		userRepository.add(user);
		
		return user;
	}

	@Override
	public User validateUser(String username, String password)
			throws UserNotFoundException, PasswordMissmatchException, UserExpiredException {
		
		User user = userRepository.get(username).orElseThrow(()->new UserNotFoundException(username));
		if(user.getPassword().equals(jaasEngine.getEncryptedPassword(password))) {
			if (user.isExpired()) {
				throw new UserExpiredException(username);				
			} else if (!user.isEnabled()) {
				throw new UserNotFoundException(username);
			}
		} else {
			throw new PasswordMissmatchException(username);
		}
		
		return user;

	}
		
	@Override
	public void deleteRole(String roleName) {		
		roleRepository.get(roleName).ifPresent(roleRepository::remove);
		
	}

	@Override
	public Set<Role> getRoles() {		
		return roleRepository.getAll();
	}

	
	public Set<User> getUsers() {		
		return userRepository.getAll();
	}
	
	@Override
	public SearchResult searchUsers(SearchCriteria criteria) {	
		
		SearchResult result = new SearchResult();
		
		Map<Parameter, Object> parameters = criteria.getParameters()
				                                    .keySet().stream()
				                                    .map(UserRepository.Parameter::valueOf)
				                                    .filter(Objects::nonNull)
				                                    .collect(Collectors.toMap(Function.identity(), k->criteria.getParameters().get(k.name())));
		
		LinkedHashMap<Parameter, Order> order = new LinkedHashMap<>(criteria.getOrder().size());
		for (Entry<String, String>  e : criteria.getOrder().entrySet()) {
			Optional.ofNullable(UserRepository.Parameter.get(e.getKey())).ifPresent(p-> {
				order.put(p, UserRepository.Order.get(e.getValue()));
			});
		}
		
		result.setTotalCount(userRepository.count(parameters));
		if (criteria.getOffset()>result.getTotalCount()) {
			result.setFounds(new HashSet<>());
		} else {
			result.setFounds(userRepository.find(parameters, order, criteria.getOffset(), criteria.getLimit() ));
		}
		
		
		return result;
	}

	@Override
	public User enableUser(String username, boolean enable) throws UserNotFoundException {
		User user = userRepository.get(username).orElseThrow(()->new UserNotFoundException(username));
		user.setEnabled(enable);
		userRepository.add(user);
		
		return user;
	}
	
	@Override
	public User setUserExpiration(String username, boolean expired) throws UserNotFoundException {
		User user = userRepository.get(username).orElseThrow(()->new UserNotFoundException(username));
		user.setExpired(expired);
		userRepository.add(user);
		
		return user;
	}
	
	@Override
	public User switchUserStatus(String username) throws UserNotFoundException {
		User user = userRepository.get(username).orElseThrow(()->new UserNotFoundException(username));
		user.setEnabled(!user.isEnabled());
		userRepository.add(user);
		
		return user;
	}

	@Override
	public void checkManagementRequirements() {
		final Logger logger = LoggerFactory.getLogger(getClass());
		
		Map<Parameter, Object> parameters = new HashMap<>(2);
		parameters.put(UserRepository.Parameter.role, Authority.ADMINISTRATOR);
		parameters.put(UserRepository.Parameter.enabled, Boolean.TRUE);
		
		int admins = userRepository.count(parameters);
		/**
		 * Adding default user 'gvadmin' if no present		
		 */
		if (admins==0) {
			logger.info("Creating a default 'gvadmin'");
			User admin;
			try {
				jaasEngine.addUser("gvadmin", "gvadmin");				
			} catch (SecurityException e) {				
				logger.info("A user named 'gvadmin' exist: restoring his default settings", e);
				
				admin = userRepository.get("gvadmin").get();
				admin.setPassword(jaasEngine.getEncryptedPassword("gvadmin"));
				admin.setPasswordTime(new Date());
				admin.setExpired(true);
				userRepository.add(admin);
				
			}
			
			admin = userRepository.get("gvadmin").get();
			admin.setEnabled(true);
			admin.getRoles().clear();
			admin.getRoles().add(new Role(Authority.ADMINISTRATOR, "Created by GV"));

			//roles required to use karaf 
			admin.getRoles().add(new Role("admin", "Created by GV"));
			admin.getRoles().add(new Role("manager", "Created by GV"));
			admin.getRoles().add(new Role("viewer", "Created by GV"));
			admin.getRoles().add(new Role("systembundles", "Created by GV"));
						
			userRepository.add(admin);
		}	
		
	}

	@Override
	public void addRole(String username, String rolename) throws InvalidRoleException, UserNotFoundException{
		try {
			jaasEngine.addRole(username, rolename);
		} catch (SecurityException e) {
			if (e.getCause() instanceof UserNotFoundException) {
				throw (UserNotFoundException) e.getCause();
			} else {
				throw e;
			}
		}
		
		
	}

	@Override
	public void revokeRole(String username, String role) throws UserNotFoundException {
		try {
			jaasEngine.deleteRole(username, role);
		} catch (SecurityException e) {
			if (e.getCause() instanceof UserNotFoundException) {
				throw (UserNotFoundException) e.getCause();
			} else {
				throw e;
		
			}
		}
	}

	@Override
	public void updateUsername(String username, String newUsername) throws UserNotFoundException, InvalidUsernameException {
		
		User user = userRepository.get(username).orElseThrow(()->new UserNotFoundException(username));
		if (username.matches(User.USERNAME_PATTERN)) {
			user.setUsername(newUsername);
		} else {
			throw new InvalidUsernameException(newUsername);
		}		
		
		userRepository.add(user);
	}
	
	

}
