# FractalX
A Framework for Static Decomposition of Modular Monolithic Applications into Microservice Deployments

Install
```
cd fractalx-parent
```
```
mvn clean install
```

Run
```
cd /Users/sathnindu/Develop/fractalx/FractalX/fractalx-test-app
```

```
mvn com.fractalx:fractalx-maven-plugin:0.1.0-SNAPSHOT:decompose
```

Proposed run command (after settings.xml configuration)
```
mvn fractalx:decompose
```