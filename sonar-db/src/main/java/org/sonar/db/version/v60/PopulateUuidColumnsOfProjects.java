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
package org.sonar.db.version.v60;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.sonar.db.Database;
import org.sonar.db.version.BaseDataChange;
import org.sonar.db.version.MassUpdate;
import org.sonar.db.version.Select;
import org.sonar.db.version.SqlStatement;

public class PopulateUuidColumnsOfProjects extends BaseDataChange {

  public PopulateUuidColumnsOfProjects(Database db) {
    super(db);
  }

  @Override
  public void execute(Context context) throws SQLException {

    Map<Long, String> componentUuidById = buildComponentUuidMap(context);
    if (componentUuidById.isEmpty()) {
      return;
    }

    populateRootUuidColumn(context, componentUuidById);
    populateCopyComponentUuidColumn(context, componentUuidById);
    populatePersonUuidColumn(context, componentUuidById);
  }

  private Map<Long, String> buildComponentUuidMap(Context context) throws SQLException {
    Map<Long, String> componentUuidById = new HashMap<>();
    context.prepareSelect("select distinct p1.id, p1.uuid from projects p1" +
      " join projects p2 on p1.id = p2.root_id" +
      " where p2.root_uuid is null")
      .scroll(row -> componentUuidById.put(row.getLong(1), row.getString(2)));
    context.prepareSelect("select distinct p1.id, p1.uuid from projects p1" +
      " join projects p2 on p1.id = p2.copy_resource_id" +
      " where p2.copy_resource_id is not null and p2.copy_component_uuid is null")
      .scroll(row -> componentUuidById.put(row.getLong(1), row.getString(2)));
    context.prepareSelect("select distinct p1.id, p1.uuid from projects p1" +
      " join projects p2 on p1.id = p2.person_id" +
      " where p2.person_id is not null and p2.developer_uuid is null")
      .scroll(row -> componentUuidById.put(row.getLong(1), row.getString(2)));
    return componentUuidById;
  }

  private void populateRootUuidColumn(Context context, Map<Long, String> componentUuidById) throws SQLException {
    // update all rows with specific root_id which have no root_uuid yet in a single update
    // this will be efficient as root_id is indexed
    MassUpdate massUpdate = context.prepareMassUpdate();
    massUpdate.select("SELECT distinct p.root_id from projects p where p.root_uuid is null");
    massUpdate.update("UPDATE projects SET root_uuid=? WHERE p_root_id=? and p.root_uuid is null");
    massUpdate.rowPluralName("root uuid of components");
    massUpdate.execute((row, update) -> handleUpdateByRooId(componentUuidById, row, update));
  }

  private boolean handleUpdateByRooId(Map<Long, String> componentUuidById, Select.Row row, SqlStatement update) throws SQLException {
    long rootId = row.getLong(1);
    String rootUuid = componentUuidById.get(rootId);

    update.setString(1, rootUuid);
    update.setLong(2, rootId);

    return true;
  }

  private void populateCopyComponentUuidColumn(Context context, Map<Long, String> componentUuidById) throws SQLException {
    MassUpdate massUpdate = context.prepareMassUpdate();
    massUpdate.select("SELECT p.id, p.copy_resource_id from projects p where p.copy_component_uuid is null and p.copy_resource_id is not null");
    massUpdate.update("UPDATE projects SET copy_component_uuid=? WHERE id=?");
    massUpdate.rowPluralName("copy component uuid of components");
    massUpdate.execute((row, update) -> this.handleUpdateById(componentUuidById, row, update));
  }

  private void populatePersonUuidColumn(Context context, Map<Long, String> componentUuidById) throws SQLException {
    MassUpdate massUpdate = context.prepareMassUpdate();
    massUpdate.select("SELECT p.id, p.person_id from projects p where p.developer_uuid is null and p.person_id is not null");
    massUpdate.update("UPDATE projects SET developer_uuid=? WHERE id=?");
    massUpdate.rowPluralName("person uuid of components");
    massUpdate.execute((row, update) -> handleUpdateById(componentUuidById, row, update));
  }

  private boolean handleUpdateById(Map<Long, String> componentUuidById, Select.Row row, SqlStatement update) throws SQLException {
    long id = row.getLong(1);
    long rootId = row.getLong(2);

    String rootUuid = componentUuidById.get(rootId);
    if (rootUuid == null) {
      return false;
    }

    update.setString(1, rootUuid);
    update.setLong(2, id);

    return true;
  }

}
