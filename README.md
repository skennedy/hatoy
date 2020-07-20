# Hazelcast playground project

A playground project to experiment with various Hazelcast features.

## Functionality used

* CP-susbsystem's [fenced locks](https://docs.hazelcast.org/docs/latest-dev/manual/html-single/index.html#fencedlock): to implement singleton tasks.
* [IMap](https://docs.hazelcast.org/docs/latest-dev/manual/html-single/index.html#map) with [entry listener](https://docs.hazelcast.org/docs/latest-dev/manual/html-single/index.html#map-listener): to observe specific state changes in the values of a distributed map.
* [ITopic pub/sub](https://docs.hazelcast.org/docs/latest-dev/manual/html-single/index.html#listening-for-topic-messages) - To implement topics and expose them via an akka-http web-socket.

## Running the example code

Form a local cluster by running 2-3 instances of the app. You can specify a port by supplying it as
an argument to the project `Main` class.

```bash
sbt "run 9999"
//...
sbt "run 10000"
//...
sbt "run 10001"
```
Once the nodes are up, one and only one of them will be holding a lock and populate a distributed
map of current weather conditions (keyed by city). One can access to the current weather condition from any
node by hitting the following endpoint:

```bash
❯ curl localhost:9999/current-weather/athens
CurrentWeather(athens,Sunny,083e3133-1525-43a3-a089-37fae3c4a18d,2020-07-14T14:16:04.025)% 
```

An `EntryUpdatedListener` has been configured to react to specific weather condition changes and
publish a list of adverts on a distributed topic. One can subscribe to the ads topic by hitting a websocket from any node in the cluster:

```bash
[nix-shell:~/code/hatoy]$ websocat ws://localhost:10000/ads
Advert(new-york,Buy this umbrella!)
Advert(barcelona,Buy this umbrella!)
Advert(london,Buy this umbrella!)
Advert(bari,Buy this umbrella!)
Advert(paris,Buy this umbrella!)
Advert(chania,Buy this umbrella!)
Advert(new-york,Buy this umbrella!)
Advert(athens,Buy this umbrella!)
Advert(paris,Buy this umbrella!)
Advert(london,Buy a disposable BBQ!)
Advert(chania,Buy this umbrella!)
Advert(london,Buy this umbrella!)
Advert(bari,Buy this umbrella!)
Advert(london,Buy a disposable BBQ!)
Advert(london,Buy this umbrella!)
```
