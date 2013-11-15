# RequestAge
### Eclipse plug-in to stress-test HTTP services

If you are a programmer and want to leverage your skills to do some stress testing, you may like *RequestAge*. These are the main features:

- stress testing scripts written in JavaScript:
  
  `function test() { req('example').get('http://wwww.example.org').go(spy); }`

- interactive: apply "throttle" and see how the server keeps up on live histograms:
  
  ![Stress testing session---request age](http://i811.photobucket.com/albums/zz35/mtopolnik/Scenario-histogram_zpsdf65ba10.png)

  ![Stress testing session---response time distribution](http://i811.photobucket.com/albums/zz35/mtopolnik/resp_dist_zpse4a05094.png)

- the histograms are visually minimalistiic, yet information packed;
- history charts allow long-term monitoring:

  ![Stress testing history---pending requests](http://i811.photobucket.com/albums/zz35/mtopolnik/pending_reqs_zps814ce9c4.png)
  
  ![Stress testing history---response time scatter chart](http://i811.photobucket.com/albums/zz35/mtopolnik/scatter_zps2b8f64a9.png)
  


- geared towards developing intuition about the tested system through rich interaction and visualisation.

###[Download](https://sourceforge.net/projects/requestage/files)

###Read more on the [RequestAge Wiki](https://github.com/mtopolnik/perftest-eclipse-plugin/wiki).


© 2013 Marko Topolnik, Inge-mark d.o.o. Licensed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html).
