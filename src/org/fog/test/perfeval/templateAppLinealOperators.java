package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup for template
 * @author Gonzalo Martinez
 *
 */
public class templateAppLinealOperators {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static int numOfSTB = 4;
	static int numOfDevicesPerSTB = 4;
	
	private static boolean isOnlyCloud = false;
	private static boolean isHierarchicalFog = false;
	private static boolean isHierarchicalFogBalancingCloud = false;
	
	private static boolean isHighRangeTypeDevice = false;
	private static boolean isMidRangeTypeDevice = false;
	private static boolean isLowRangeRangeTypeDevice = false;
	
	public static void main(String[] args) {

		Log.printLine("[Starting] Aplication...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "sampleApp"; // identifier of the application
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			createFogDevices(broker.getId(), appId);
			
			Controller controller = null;
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("m")){ // names of all Smart Cameras start with 'm' 
					moduleMapping.addModuleToDevice("operator_1", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
				}
			}
			moduleMapping.addModuleToDevice("operator_3", "cloud"); // fixing instances of User Interface module in the Cloud
			if(isOnlyCloud){
				moduleMapping.addModuleToDevice("operator_1", "cloud");
				moduleMapping.addModuleToDevice("operator_2", "cloud");
				moduleMapping.addModuleToDevice("operator_3", "cloud");
			}
			
			controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			controller.submitApplication(application, 
					(isOnlyCloud)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();
			CloudSim.stopSimulation();

			Log.printLine("[Finished]");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("[Error] Unwanted errors happen");
		}
	}
	
	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 
				100, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 
				10000, 10000, 1, 0.0, 107.339, 83.4333);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		for(int i=0;i<numOfSTB;i++){
			addSTB(i+"", userId, appId, proxy.getId());
		}
	}

	private static FogDevice addSTB(String id, int userId, String appId, int parentId) {
		FogDevice STB = createFogDevice("STB-"+id, 2800, 4000, 
				10000, 10000, 1, 0.0, 107.339, 83.4333);
		fogDevices.add(STB);
		STB.setUplinkLatency(2); // latency of connection between STB and proxy server is 2 ms
		for(int i=0;i<numOfDevicesPerSTB;i++){
			String mobileId = id+"-"+i;
			FogDevice mobileDevice = addDevices(mobileId, userId, appId, STB.getId()); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
			mobileDevice.setUplinkLatency(2); // latency of connection between MobileDevices and STB is 2 ms
			fogDevices.add(mobileDevice);
		}
		STB.setParentId(parentId);
		return STB;
	}
	
	private static FogDevice addDevices(String id, int userId, String appId, int parentId) {

		if(isHighRangeTypeDevice) {
			FogDevice mobileDevice = createFogDevice("MobileDevice-"+id, 500, 1000, 
					10000, 10000, 3, 0, 87.53, 82.44);
			mobileDevice.setParentId(parentId);
			Sensor sensor = new Sensor("sensor-"+id, "TUPLE_SENSOR_TO_OPERATOR_1", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of mobileDevice (sensor) follows a deterministic distribution
			sensors.add(sensor);
			Actuator actuatorDevice = new Actuator("actuator-"+id, userId, appId, "ACTUATOR");
			actuators.add(actuatorDevice);
			sensor.setGatewayDeviceId(mobileDevice.getId());
			sensor.setLatency(1.0);  // latency of connection between sensor and the parent Device is 1 ms
			actuatorDevice.setGatewayDeviceId(mobileDevice.getId());
			actuatorDevice.setLatency(1.0);  // latency of connection between Actuator and the parent Device is 1 ms
			return mobileDevice;
		} else if(isMidRangeTypeDevice) {
			FogDevice mobileDevice = createFogDevice("MobileDevice-"+id, 500, 1000, 
					10000, 10000, 3, 0, 87.53, 82.44);
			mobileDevice.setParentId(parentId);
			Sensor sensor = new Sensor("sensor-"+id, "TUPLE_SENSOR_TO_OPERATOR_1", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of mobileDevice (sensor) follows a deterministic distribution
			sensors.add(sensor);
			Actuator actuatorDevice = new Actuator("actuator-"+id, userId, appId, "ACTUATOR");
			actuators.add(actuatorDevice);
			sensor.setGatewayDeviceId(mobileDevice.getId());
			sensor.setLatency(1.0);  // latency of connection between sensor and the parent Device is 1 ms
			actuatorDevice.setGatewayDeviceId(mobileDevice.getId());
			actuatorDevice.setLatency(1.0);  // latency of connection between Actuator and the parent Device is 1 ms
			return mobileDevice;
		} else {
			FogDevice mobileDevice = createFogDevice("MobileDevice-"+id, 500, 1000, 
					10000, 10000, 3, 0, 87.53, 82.44);
			mobileDevice.setParentId(parentId);
			Sensor sensor = new Sensor("sensor-"+id, "TUPLE_SENSOR_TO_OPERATOR_1", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of mobileDevice (sensor) follows a deterministic distribution
			sensors.add(sensor);
			Actuator actuatorDevice = new Actuator("actuator-"+id, userId, appId, "ACTUATOR");
			actuators.add(actuatorDevice);
			sensor.setGatewayDeviceId(mobileDevice.getId());
			sensor.setLatency(1.0);  // latency of connection between sensor and the parent Device is 1 ms
			actuatorDevice.setGatewayDeviceId(mobileDevice.getId());
			actuatorDevice.setLatency(1.0);  // latency of connection between Actuator and the parent Device is 1 ms
			return mobileDevice;			
		}
	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, 
			double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		// Adding modules (vertices) to the application model (directed graph)
		application.addAppModule("operator_1", 10);
		application.addAppModule("operator_2", 10);
		application.addAppModule("operator_3", 10);
				
		// Connecting the application modules (vertices) in the application model (directed graph) with edges
		application.addAppEdge("TUPLE_SENSOR_TO_OPERATOR_1", "operator_1", 1000, 20000, 
				"TUPLE_SENSOR_TO_OPERATOR_1", Tuple.UP, AppEdge.SENSOR); // adding edge from TUPLE_SENSOR_TO_OPERATOR_1 (sensor) to Motion Detector module carrying tuples of type TUPLE_SENSOR_TO_OPERATOR_1
		application.addAppEdge("operator_1", "operator_2", 2000, 2000, 
				"TUPLE_OPERATOR1_TO_OPERATOR2", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type TUPLE_OPERATOR1_TO_OPERATOR2
		application.addAppEdge("operator_2", "operator_3", 500, 2000,
				"TUPLE_OPERATOR2_TO_OPERATOR3", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type TUPLE_OPERATOR2_TO_OPERATOR3a
		application.addAppEdge("operator_3", "ACTUATOR", 100, 28, 100, 
				"TUPLE_OPERATOR3_TO_ACTUATOR", Tuple.DOWN, AppEdge.ACTUATOR); // adding edge from Object Tracker to PTZ CONTROL (actuator) carrying tuples of type TUPLE_OPERATOR3_TO_ACTUATOR
		
		// Defining the input-output relationships (represented by selectivity) of the application modules. 
		application.addTupleMapping("operator_1", "TUPLE_SENSOR_TO_OPERATOR_1", "TUPLE_OPERATOR1_TO_OPERATOR2", 
				new FractionalSelectivity(1.0)); // 1.0 tuples of type TUPLE_OPERATOR1_TO_OPERATOR2 are emitted by Motion Detector module per incoming tuple of type TUPLE_SENSOR_TO_OPERATOR_1
		application.addTupleMapping("operator_2", "TUPLE_OPERATOR1_TO_OPERATOR2", "TUPLE_OPERATOR2_TO_OPERATOR3", 
				new FractionalSelectivity(1.0)); // 1.0 tuples of type TUPLE_OPERATOR2_TO_OPERATOR3b are emitted by Object Detector module per incoming tuple of type TUPLE_OPERATOR1_TO_OPERATOR2
	
		
		// Defining application loops
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){
			{add("operator_1");
			add("operator_2");
			add("operator_3");}
			});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{
			add(loop1);
			}
		};
		
		application.setLoops(loops);
		return application;
	}
}