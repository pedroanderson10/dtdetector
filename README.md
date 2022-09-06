This project is a modified version of DependentTestDetector (dtdetector) found at our  
[testisolation repository](https://code.google.com/p/testisolation).
The version present in the repository is from October 3rd, 2013 revision number 454. 

Please visit the 
[User manual](https://github.com/winglam/dtdetector/wiki/User-manual)    
and 
[BuiltInTools](https://github.com/winglam/dtdetector/wiki/BuiltInTools) 
pages for more information about how to use the dtdetector.

This version of the dtdetector differs from the original one in two important ways.
1. Can output the time each test took to execute. These changes allowed us 
to create test parallelization orders based on the time tests took to execute. 
2. This version no longer hangs when running tests that spawns new threads but does not
kill the threads.

In order to accomplish the changes described, the following files of the dtdetector were changed: 

    /dt-detector/src/edu/washington/cs/dt/OneTestExecResult.java 
    /dt-detector/src/edu/washington/cs/dt/runners/FixedOrderRunner.java
    /dt-detector/src/edu/washington/cs/dt/util/TestExecUtils.java 
    /dt-detector/src/edu/washington/cs/dt/util/TestRunnerWrapper.java 
    /dt-detector/src/edu/washington/cs/dt/util/TestRunnerWrapperFileInputs.java 
    /dt-detector/src/edu/washington/cs/dt/main/ImpactMain.java   
