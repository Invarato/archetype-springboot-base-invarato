# Archetype of Ramon Invarato

https://maven.apache.org/guides/mini/guide-creating-archetypes.html



## Quick download (Beta)

Generate a new Maven project based on a specific archetype that you specify. The values for archetypeGroupId,
archetypeArtifactId and archetypeVersion identify the archetype from which your new project is created. The groupId and
artifactId parameters determine the identity of the new project you are creating.

* archetypeGroupId: The group ID of the archetype that was used to create this project.
* archetypeArtifactId: The artifact ID of the archetype that was used to create this project.
* archetypeVersion: The version of the archetype that was used to create this project.
* groupId: The group ID of the new project.
* artifactId: The artifact ID of the new project.

```shell
mvn archetype:generate                                  \
  -DarchetypeGroupId=<archetype-groupId>                \
  -DarchetypeArtifactId=<archetype-artifactId>          \
  -DarchetypeVersion=<archetype-version>                \
  -DgroupId=<my.groupid>                                \
  -DartifactId=<my-artifactId>
```


Example:
```shell
rm -r ~/.m2/repository/com/jarroba/archetype-springboot-base-invarato/1.0-SNAPSHOT
rm -r myProyect

mvn clean install

mvn archetype:generate                                  \
  -DarchetypeGroupId=com.jarroba                         \
  -DarchetypeArtifactId=archetype-springboot-base-invarato                      \
  -DarchetypeVersion=1.0-SNAPSHOT                        \
  -DgroupId=mi.domain                                     \
  -DartifactId=myProyect
```

Or

````shell
wsl bash restart_build.sh
````



# Otros


````shell
wsl maven clean install
````
