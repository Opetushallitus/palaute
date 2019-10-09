# Palaute

Service for gathering user feedback

## How to build as standalone package

`mvn package`

Run 

`java -jar target/palaute-0.0.1-SNAPSHOT-jar-with-dependencies.jar`

## Developping

Set preferred logging level: `export TIMBRE_LEVEL=info` or `set -x mvn TIMBRE_LEVEL info`

Start PostgreSQL in Docker 
```
docker pull postgres

docker run --rm --name palaute-test-db -e POSTGRES_USER=oph -e POSTGRES_PASSWORD=oph -d -p 5436:5432 -v $HOME/docker/volumes/postgres:/var/lib/postgresql/data  postgres

``` 
or
```
docker pull postgres

docker run --rm --name palaute-test-db -e POSTGRES_USER=oph -e POSTGRES_PASSWORD=oph -d -p 5436:5432 postgres

``` 

Start watching frontend changes with `npm run start` 

Start backend with `mvn clojure:compile clojure:run` (You have to run `mvn package` before this)

Open in browser (http://localhost:9000)

