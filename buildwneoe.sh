export NB=../neoebuild/
java -cp $NB/ant.jar:$NB/neoebuild.jar:"$JAVA_HOME/lib/tools.jar" neoe.build.BuildMain $*
