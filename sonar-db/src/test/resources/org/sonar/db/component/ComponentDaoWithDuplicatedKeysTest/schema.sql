CREATE TABLE "PROJECTS" (
  "ID" INTEGER NOT NULL GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1),
  "KEE" VARCHAR(400),
  "UUID" VARCHAR(50) NOT NULL,
  "ROOT_UUID" VARCHAR(50) NOT NULL,
  "PROJECT_UUID" VARCHAR(50),
  "MODULE_UUID" VARCHAR(50),
  "MODULE_UUID_PATH" VARCHAR(4000),
  "NAME" VARCHAR(2000),
  "DESCRIPTION" VARCHAR(2000),
  "ENABLED" BOOLEAN NOT NULL DEFAULT TRUE,
  "SCOPE" VARCHAR(3),
  "QUALIFIER" VARCHAR(10),
  "DEPRECATED_KEE" VARCHAR(400),
  "PATH" VARCHAR(2000),
  "LANGUAGE" VARCHAR(20),
  "COPY_COMPONENT_UUID" VARCHAR(50),
  "LONG_NAME" VARCHAR(2000),
  "DEVELOPER_UUID" VARCHAR(50),
  "CREATED_AT" TIMESTAMP,
  "AUTHORIZATION_UPDATED_AT" BIGINT
);

CREATE INDEX "PROJECTS_KEE" ON "PROJECTS" ("KEE");

CREATE INDEX "PROJECTS_ROOT_UUID" ON "PROJECTS" ("ROOT_UUID");

CREATE UNIQUE INDEX "PROJECTS_UUID" ON "PROJECTS" ("UUID");

CREATE INDEX "PROJECTS_PROJECT_UUID" ON "PROJECTS" ("PROJECT_UUID");

CREATE INDEX "PROJECTS_MODULE_UUID" ON "PROJECTS" ("MODULE_UUID");

CREATE INDEX "PROJECTS_QUALIFIER" ON "PROJECTS" ("QUALIFIER");
