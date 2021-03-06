/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db.permission;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static org.sonar.db.DatabaseUtils.executeLargeInputsWithoutOutput;

import com.google.common.annotations.VisibleForTesting;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.sonar.api.security.DefaultGroups;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;
import org.sonar.db.MyBatis;
import org.sonar.db.permission.template.PermissionTemplateCharacteristicMapper;

public class PermissionTemplateDao implements Dao {

  public static final String QUERY_PARAMETER = "query";
  public static final String TEMPLATE_ID_PARAMETER = "templateId";
  private static final String ANYONE_GROUP_PARAMETER = "anyoneGroup";

  private final MyBatis myBatis;
  private final System2 system;

  public PermissionTemplateDao(MyBatis myBatis, System2 system) {
    this.myBatis = myBatis;
    this.system = system;
  }

  /**
   * @return a paginated list of users.
   */
  public List<UserWithPermissionDto> selectUsers(PermissionQuery query, Long templateId, int offset, int limit) {
    DbSession session = myBatis.openSession(false);
    try {
      return selectUsers(session, query, templateId, offset, limit);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  /**
   * @return a paginated list of users.
   */
  public List<UserWithPermissionDto> selectUsers(DbSession session, PermissionQuery query, Long templateId, int offset, int limit) {
    Map<String, Object> params = newHashMap();
    params.put(QUERY_PARAMETER, query);
    params.put(TEMPLATE_ID_PARAMETER, templateId);
    return mapper(session).selectUsers(params, new RowBounds(offset, limit));
  }

  public int countUsers(DbSession session, PermissionQuery query, Long templateId) {
    Map<String, Object> params = newHashMap();
    params.put(QUERY_PARAMETER, query);
    params.put(TEMPLATE_ID_PARAMETER, templateId);
    return mapper(session).countUsers(params);
  }

  @VisibleForTesting
  List<UserWithPermissionDto> selectUsers(PermissionQuery query, Long templateId) {
    return selectUsers(query, templateId, 0, Integer.MAX_VALUE);
  }

  /**
   * 'Anyone' group is not returned when it has not the asked permission.
   * Membership parameter from query is not taking into account in order to deal more easily with the 'Anyone' group.
   * @return a non paginated list of groups.
   */
  public List<GroupWithPermissionDto> selectGroups(DbSession session, PermissionQuery query, Long templateId) {
    return selectGroups(session, query, templateId, 0, Integer.MAX_VALUE);
  }

  public List<GroupWithPermissionDto> selectGroups(DbSession session, PermissionQuery query, Long templateId, int offset, int limit) {
    Map<String, Object> params = groupsParameters(query, templateId);
    return mapper(session).selectGroups(params, new RowBounds(offset, limit));
  }

  public List<GroupWithPermissionDto> selectGroups(PermissionQuery query, Long templateId) {
    DbSession session = myBatis.openSession(false);
    try {
      return selectGroups(session, query, templateId);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public int countGroups(DbSession session, PermissionQuery query, long templateId) {
    return countGroups(session, query, templateId, null);
  }

  private static int countGroups(DbSession session, PermissionQuery query, long templateId, @Nullable String groupName) {
    Map<String, Object> parameters = groupsParameters(query, templateId);
    if (groupName != null) {
      parameters.put("groupName", groupName.toUpperCase(Locale.ENGLISH));
    }
    return mapper(session).countGroups(parameters);
  }

  public boolean hasGroup(DbSession session, PermissionQuery query, long templateId, String groupName) {
    return countGroups(session, query, templateId, groupName) > 0;
  }

  private static Map<String, Object> groupsParameters(PermissionQuery query, Long templateId) {
    Map<String, Object> params = newHashMap();
    params.put(QUERY_PARAMETER, query);
    params.put(TEMPLATE_ID_PARAMETER, templateId);
    params.put("anyoneGroup", DefaultGroups.ANYONE);
    params.put("projectAdminPermission", UserRole.ADMIN);
    return params;
  }

  @CheckForNull
  public PermissionTemplateDto selectByUuid(DbSession session, String templateUuid) {
    return mapper(session).selectByUuid(templateUuid);
  }

  @CheckForNull
  public PermissionTemplateDto selectByUuid(String templateUuid) {
    DbSession session = myBatis.openSession(false);
    try {
      return selectByUuid(session, templateUuid);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  @CheckForNull
  public PermissionTemplateDto selectByUuidWithUserAndGroupPermissions(DbSession session, String templateUuid) {
    PermissionTemplateDto permissionTemplate;
    PermissionTemplateMapper mapper = mapper(session);
    permissionTemplate = mapper.selectByUuid(templateUuid);
    PermissionTemplateDto templateUsersPermissions = mapper.selectTemplateUsersPermissions(templateUuid);
    if (templateUsersPermissions != null) {
      permissionTemplate.setUsersPermissions(templateUsersPermissions.getUsersPermissions());
    }
    PermissionTemplateDto templateGroupsPermissions = mapper.selectTemplateGroupsPermissions(templateUuid);
    if (templateGroupsPermissions != null) {
      permissionTemplate.setGroupsByPermission(templateGroupsPermissions.getGroupsPermissions());
    }
    return permissionTemplate;
  }

  @CheckForNull
  public PermissionTemplateDto selectByUuidWithUserAndGroupPermissions(String templateUuid) {
    DbSession session = myBatis.openSession(false);
    try {
      return selectByUuidWithUserAndGroupPermissions(session, templateUuid);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public List<PermissionTemplateDto> selectAll(DbSession session, String nameMatch) {
    String uppercaseNameMatch = toUppercaseSqlQuery(nameMatch);
    return mapper(session).selectAll(uppercaseNameMatch);
  }

  public List<PermissionTemplateDto> selectAll(DbSession session) {
    return mapper(session).selectAll(null);
  }

  public List<PermissionTemplateDto> selectAll() {
    DbSession session = myBatis.openSession(false);
    try {
      return selectAll(session);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public int countAll(DbSession dbSession, String nameQuery) {
    String upperCasedNameQuery = toUppercaseSqlQuery(nameQuery);

    return mapper(dbSession).countAll(upperCasedNameQuery);
  }

  private static String toUppercaseSqlQuery(String nameMatch) {
    String wildcard = "%";
    return format("%s%s%s", wildcard, nameMatch.toUpperCase(Locale.ENGLISH), wildcard);

  }

  public PermissionTemplateDto insert(DbSession session, PermissionTemplateDto permissionTemplate) {
    mapper(session).insert(permissionTemplate);
    session.commit();

    return permissionTemplate;
  }

  /**
   * Each row returns a #{@link CountByProjectAndPermissionDto}
   */
  public void usersCountByTemplateIdAndPermission(DbSession dbSession, List<Long> templateIds, ResultHandler resultHandler) {
    Map<String, Object> parameters = new HashMap<>(1);

    executeLargeInputsWithoutOutput(
      templateIds,
      partitionedTemplateIds -> {
        parameters.put("templateIds", partitionedTemplateIds);
        mapper(dbSession).usersCountByTemplateIdAndPermission(parameters, resultHandler);
        return null;
      });
  }

  /**
   * Each row returns a #{@link CountByProjectAndPermissionDto}
   */
  public void groupsCountByTemplateIdAndPermission(DbSession dbSession, List<Long> templateIds, ResultHandler resultHandler) {
    Map<String, Object> parameters = new HashMap<>(2);
    parameters.put(ANYONE_GROUP_PARAMETER, DefaultGroups.ANYONE);

    executeLargeInputsWithoutOutput(
      templateIds,
      partitionedTemplateIds -> {
        parameters.put("templateIds", partitionedTemplateIds);
        mapper(dbSession).groupsCountByTemplateIdAndPermission(parameters, resultHandler);
        return null;
      });
  }

  public void deleteById(DbSession session, long templateId) {
    PermissionTemplateMapper mapper = mapper(session);
    mapper.deleteUserPermissions(templateId);
    mapper.deleteGroupPermissions(templateId);
    session.getMapper(PermissionTemplateCharacteristicMapper.class).deleteByTemplateId(templateId);
    mapper.delete(templateId);
  }

  /**
   * @deprecated since 5.2 use {@link #update(DbSession, PermissionTemplateDto)}
   */
  @Deprecated
  public void update(Long templateId, String templateName, @Nullable String description, @Nullable String projectPattern) {
    PermissionTemplateDto permissionTemplate = new PermissionTemplateDto()
      .setId(templateId)
      .setName(templateName)
      .setDescription(description)
      .setKeyPattern(projectPattern)
      .setUpdatedAt(now());

    DbSession session = myBatis.openSession(false);
    try {
      update(session, permissionTemplate);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public PermissionTemplateDto update(DbSession session, PermissionTemplateDto permissionTemplate) {
    mapper(session).update(permissionTemplate);
    session.commit();

    return permissionTemplate;
  }

  /**
   * @deprecated since 5.2 {@link #insertUserPermission(DbSession, Long, Long, String)}
   */
  @Deprecated
  public void insertUserPermission(Long templateId, Long userId, String permission) {
    DbSession session = myBatis.openSession(false);
    try {
      insertUserPermission(session, templateId, userId, permission);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void insertUserPermission(DbSession session, Long templateId, Long userId, String permission) {
    PermissionTemplateUserDto permissionTemplateUser = new PermissionTemplateUserDto()
      .setTemplateId(templateId)
      .setUserId(userId)
      .setPermission(permission)
      .setCreatedAt(now())
      .setUpdatedAt(now());

    mapper(session).insertUserPermission(permissionTemplateUser);
    session.commit();
  }

  public void insertUserPermission(DbSession session, PermissionTemplateUserDto permissionTemplateUserDto) {
    mapper(session).insertUserPermission(permissionTemplateUserDto);
  }

  /**
   * @deprecated since 5.2 {@link #deleteUserPermission(DbSession, Long, Long, String)}
   */
  @Deprecated
  public void deleteUserPermission(Long templateId, Long userId, String permission) {
    DbSession session = myBatis.openSession(false);
    try {
      deleteUserPermission(session, templateId, userId, permission);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void deleteUserPermission(DbSession session, Long templateId, Long userId, String permission) {
    PermissionTemplateUserDto permissionTemplateUser = new PermissionTemplateUserDto()
      .setTemplateId(templateId)
      .setPermission(permission)
      .setUserId(userId);
    mapper(session).deleteUserPermission(permissionTemplateUser);
    session.commit();
  }

  /**
   * @deprecated since 5.2 use {@link #insertGroupPermission(DbSession, Long, Long, String)}
   */
  @Deprecated
  public void insertGroupPermission(Long templateId, @Nullable Long groupId, String permission) {
    DbSession session = myBatis.openSession(false);
    try {
      insertGroupPermission(session, templateId, groupId, permission);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void insertGroupPermission(DbSession session, Long templateId, @Nullable Long groupId, String permission) {
    PermissionTemplateGroupDto permissionTemplateGroup = new PermissionTemplateGroupDto()
      .setTemplateId(templateId)
      .setPermission(permission)
      .setGroupId(groupId)
      .setCreatedAt(now())
      .setUpdatedAt(now());
    mapper(session).insertGroupPermission(permissionTemplateGroup);
    session.commit();
  }

  public void insertGroupPermission(DbSession session, PermissionTemplateGroupDto permissionTemplateGroup) {
    mapper(session).insertGroupPermission(permissionTemplateGroup);
  }

  /**
   * @deprecated since 5.2 use {@link #deleteGroupPermission(DbSession, Long, Long, String)}
   */
  @Deprecated
  public void deleteGroupPermission(Long templateId, @Nullable Long groupId, String permission) {
    DbSession session = myBatis.openSession(false);
    try {
      deleteGroupPermission(session, templateId, groupId, permission);
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public void deleteGroupPermission(DbSession session, Long templateId, @Nullable Long groupId, String permission) {
    PermissionTemplateGroupDto permissionTemplateGroup = new PermissionTemplateGroupDto()
      .setTemplateId(templateId)
      .setPermission(permission)
      .setGroupId(groupId);
    mapper(session).deleteGroupPermission(permissionTemplateGroup);
    session.commit();
  }

  /**
   * Load permission template and load associated collections of users and groups permissions
   */
  @VisibleForTesting
  PermissionTemplateDto selectPermissionTemplateWithPermissions(DbSession session, String templateUuid) {
    PermissionTemplateDto permissionTemplateDto = selectByUuid(session, templateUuid);
    if (permissionTemplateDto == null) {
      throw new IllegalArgumentException("Could not retrieve permission template with uuid " + templateUuid);
    }
    PermissionTemplateDto templateWithPermissions = selectByUuidWithUserAndGroupPermissions(session, permissionTemplateDto.getUuid());
    if (templateWithPermissions == null) {
      throw new IllegalArgumentException("Could not retrieve permissions for template with uuid " + templateUuid);
    }
    return templateWithPermissions;
  }

  public PermissionTemplateDto selectByName(DbSession dbSession, String name) {
    return mapper(dbSession).selectByName(name.toUpperCase(Locale.ENGLISH));
  }

  /**
   * Remove a group from all templates (used when removing a group)
   */
  public void deleteByGroup(SqlSession session, Long groupId) {
    session.getMapper(PermissionTemplateMapper.class).deleteByGroupId(groupId);
  }

  private Date now() {
    return new Date(system.now());
  }

  private static PermissionTemplateMapper mapper(SqlSession session) {
    return session.getMapper(PermissionTemplateMapper.class);
  }
}
