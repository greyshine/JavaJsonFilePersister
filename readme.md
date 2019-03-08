# JsonPersister
_CRUD operations for Java Objects with the target as Json files_

_Maven usage_
use in your _pom.xml_ like this:

    <?xml version="1.0" encoding="UTF-8"?>
    <project xmlns="http://maven.apache.org/POM/4.0.0"
	    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	    <modelVersion>4.0.0</modelVersion>
	
	    <groupId>com.yourGroup</groupId>
	    <artifactId>yourArtefact</artifactId>
	    <version>0.1</version>
	
	    <dependencies>
		    <dependency>
			    <groupId>de.greyshine</groupId>
			    <artifactId>json-persister</artifactId>
			    <version>1.0</version>
		    </dependency>
	   </dependencies>
   
	   <repositories>
	       <repository>
		       <id>jitpack.io</id>
		       <url>https://jitpack.io</url>
		   </repository>
	    </repositories>

    </project>