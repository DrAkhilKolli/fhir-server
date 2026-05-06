This directory contains optional Liberty config snippets that are disabled by default.

# Datasource snippets used for server integration tests

```
datasource-derby.xml
datasource-postgresql.xml
```

These files are copied to the overrides folder and renamed to datasource.xml.

Only one of these datasource definition files should be copied into the target Liberty configDropins/overrides folder. If more than one of these is present at the same time it will break the Liberty configuration because the datasource ids and JNDI location are common among the files.

# Other optional snippets

```
jwtRS.xml
cors.xml
jvm.options
jvm-dev.options
```

`jwtRS.xml` is a sample MicroProfile JWT protected-resource configuration for using an external OAuth 2.0 / OpenID Connect provider such as Keycloak with the FHIR server.
