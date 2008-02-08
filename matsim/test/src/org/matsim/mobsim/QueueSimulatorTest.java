/* *********************************************************************** *
 * project: org.matsim.*
 * QueueSimulatorTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.mobsim;


import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.basic.v01.Id;
import org.matsim.config.Config;
import org.matsim.events.Events;
import org.matsim.gbl.Gbl;
import org.matsim.network.Link;
import org.matsim.plans.Leg;
import org.matsim.plans.Person;
import org.matsim.plans.Plan;
import org.matsim.plans.Plans;
import org.matsim.plans.Route;
import org.matsim.testcases.MatsimTestCase;
import org.matsim.utils.misc.Time;
import org.matsim.world.World;

public class QueueSimulatorTest extends MatsimTestCase {

	/**
	 * Tests that the flow capacity can be reached (but not exceeded) by
	 * agents driving over a link.
	 *
	 * @author mrieser
	 */
	public void testFlowCapacityDriving() {
		Config config = Gbl.createConfig(null);
		config.simulation().setFlowCapFactor(1.0);
		config.simulation().setStorageCapFactor(1.0);
		World world = Gbl.getWorld();

		/* build network */
		QueueNetworkLayer network = new QueueNetworkLayer();
		world.setNetworkLayer(network);
		network.setCapacityPeriod("1:00:00");
		network.createNode("1", "0", "0", null);
		network.createNode("2", "100", "0", null);
		network.createNode("3", "1100", "0", null);
		network.createNode("4", "1200", "0", null);
		Link link1 = network.createLink("1", "1", "2", "100", "10", "60000", "9", null, null);
		/* ------ */ network.createLink("2", "2", "3", "1000", "10", "6000", "2", null, null);
		Link link3 = network.createLink("3", "3", "4", "100", "10", "60000", "9", null, null);

		/* build plans */
		Plans plans = new Plans(Plans.NO_STREAMING);

		try {
			// add a first person with leg from link1 to link3, let it start early, so the simulation can accumulate buffer capacity
			Person person = new Person(new Id(0), "m", 35, "yes", "yes", "yes");
			Plan plan = person.createPlan(null, "yes");
			plan.createAct("h", 199.0, 0.0, link1, 0, 6*3600-500, 6*3600-500, false);
			Leg leg = plan.createLeg(1, "car", 6*3600-500, Time.UNDEFINED_TIME, Time.UNDEFINED_TIME);
			Route route = new Route();
			route.setRoute("2 3");
			leg.setRoute(route);
			plan.createAct("w", 99.0, 0.0, link3, 6*3600-390, 24*36000, Time.UNDEFINED_TIME, true);
			plans.addPerson(person);

			// add a lot of other persons with legs from link1 to link3, starting at 6:30
			for (int i = 1; i <= 10000; i++) {
				person = new Person(new Id(i), "m", 35, "yes", "yes", "yes");
				plan = person.createPlan(null, "yes");
				/* exact dep. time: 6:28:18. The agents needs:
				 * - at the specified time, the agent goes into the waiting list, and if space is available, into
				 * the buffer of link 1.
				 * - 1 sec later, it leaves the buffer on link 1 and enters link 2
				 * - the agent takes 100 sec. to travel along link 2, after which it gets placed in the buffer of link 2
				 * - 1 sec later, the agent leaves the buffer on link 2 (if flow-cap allows this) and enters link 3
				 * - as we measure the vehicles leaving link 2, and the first veh should leave at exactly 6:30, it has
				 * to start 1 + 100 + 1 = 102 secs earlier.
				 * So, the start time is 7*3600 - 1800 - 102 = 7*3600 - 1902
				 */
				plan.createAct("h", 99.0, 0.0, link1, 0, 7*3600-1902, 7*3600-1902, false);
				leg = plan.createLeg(1, "car", 7*3600-1902, Time.UNDEFINED_TIME, Time.UNDEFINED_TIME);
				route = new Route();
				route.setRoute("2 3");
				leg.setRoute(route);
				plan.createAct("w", 99.0, 0.0, link3, 7*3600-1790, 24*36000, Time.UNDEFINED_TIME, true);
				plans.addPerson(person);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		/* build events */
		Events events = new Events();
		VolumesAnalyzer vAnalyzer = new VolumesAnalyzer(3600, 9*3600, network);
		events.addHandler(vAnalyzer);

		/* run sim */
		QueueSimulation sim = new QueueSimulation(network, plans, events);
		sim.run();

		/* finish */
		int[] volume = vAnalyzer.getVolumesForLink("2");
		System.out.println("#vehicles 6-7: " + Integer.toString(volume[6]));
		System.out.println("#vehicles 7-8: " + Integer.toString(volume[7]));
		System.out.println("#vehicles 8-9: " + Integer.toString(volume[8]));

		assertEquals(3000, volume[6]); // we should have half of the maximum flow in this hour
		assertEquals(6000, volume[7]); // we should have maximum flow in this hour
		assertEquals(1000, volume[8]); // all the rest
	}

	/**
	 * Tests that the flow capacity can be reached (but not exceeded) by
	 * agents starting on a link. Due to the different handling of these
	 * agents and their direct placing in the Buffer, it makes sense to
	 * test this specifically.
	 *
	 * @author mrieser
	 */
	public void testFlowCapacityStarting() {
		Config config = Gbl.createConfig(null);
		config.simulation().setFlowCapFactor(1.0);
		config.simulation().setStorageCapFactor(1.0);
		World world = Gbl.getWorld();

		/* build network */
		QueueNetworkLayer network = new QueueNetworkLayer();
		world.setNetworkLayer(network);
		network.setCapacityPeriod("1:00:00");
		network.createNode("1", "0", "0", null);
		network.createNode("2", "100", "0", null);
		network.createNode("3", "1100", "0", null);
		network.createNode("4", "1200", "0", null);
		Link link1 = network.createLink("1", "1", "2", "100", "10", "60000", "9", null, null);
		Link link2 = network.createLink("2", "2", "3", "1000", "10", "6000", "2", null, null);
		Link link3 = network.createLink("3", "3", "4", "100", "10", "60000", "9", null, null);

		/* build plans */
		Plans plans = new Plans(Plans.NO_STREAMING);

		try {
			// add a first person with leg from link1 to link3, let it start early, so the simulation can accumulate buffer capacity
			Person person = new Person(new Id(0), "m", 35, "yes", "yes", "yes");
			Plan plan = person.createPlan(null, "yes");
			plan.createAct("h", 199.0, 0.0, link1, 0, 6*3600-500, 6*3600-500, false);
			Leg leg = plan.createLeg(1, "car", 6*3600-500, Time.UNDEFINED_TIME, Time.UNDEFINED_TIME);
			Route route = new Route();
			route.setRoute("2 3");
			leg.setRoute(route);
			plan.createAct("w", 99.0, 0.0, link3, 6*3600-390, 24*36000, Time.UNDEFINED_TIME, true);
			plans.addPerson(person);

			// add a lot of persons with legs from link2 to link3
			for (int i = 1; i <= 10000; i++) {
				person = new Person(new Id(i), "m", 35, "yes", "yes", "yes");
				plan = person.createPlan(null, "yes");
				plan.createAct("h", 99.0, 0.0, link2, 0, 7*3600 - 1801, 7*3600 - 1801, false);
				leg = plan.createLeg(1, "car", 7*3600 - 1801, Time.UNDEFINED_TIME, Time.UNDEFINED_TIME);
				route = new Route();
				route.setRoute("3");
				leg.setRoute(route);
				plan.createAct("w", 99.0, 0.0, link3, 7*3600-1790, 24*36000, Time.UNDEFINED_TIME, true);
				plans.addPerson(person);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		/* build events */
		Events events = new Events();
		VolumesAnalyzer vAnalyzer = new VolumesAnalyzer(3600, 9*3600, network);
		events.addHandler(vAnalyzer);

		/* run sim */
		QueueSimulation sim = new QueueSimulation(network, plans, events);
		sim.run();

		/* finish */
		int[] volume = vAnalyzer.getVolumesForLink("2");
		System.out.println("#vehicles 6-7: " + Integer.toString(volume[6]));
		System.out.println("#vehicles 7-8: " + Integer.toString(volume[7]));
		System.out.println("#vehicles 8-9: " + Integer.toString(volume[8]));

		assertEquals(3000, volume[6]); // we should have half of the maximum flow in this hour
		assertEquals(6000, volume[7]); // we should have maximum flow in this hour
		assertEquals(1000, volume[8]); // all the rest
	}

	/**
	 * Tests that the flow capacity of a link can be reached (but not exceeded) by
	 * agents starting on that link or driving through that link. This especially
	 * insures that the flow capacity measures both kinds (starting, driving) together.
	 *
	 * @author mrieser
	 */
	public void testFlowCapacityMixed() {
		Config config = Gbl.createConfig(null);
		config.simulation().setFlowCapFactor(1.0);
		config.simulation().setStorageCapFactor(1.0);
		World world = Gbl.getWorld();

		/* build network */
		QueueNetworkLayer network = new QueueNetworkLayer();
		world.setNetworkLayer(network);
		network.setCapacityPeriod("1:00:00");
		network.createNode("1", "0", "0", null);
		network.createNode("2", "100", "0", null);
		network.createNode("3", "1100", "0", null);
		network.createNode("4", "1200", "0", null);
		Link link1 = network.createLink("1", "1", "2", "100", "10", "60000", "9", null, null);
		Link link2 = network.createLink("2", "2", "3", "1000", "10", "6000", "2", null, null);
		Link link3 = network.createLink("3", "3", "4", "100", "10", "60000", "9", null, null);

		/* build plans */
		Plans plans = new Plans(Plans.NO_STREAMING);

		try {
			// add a first person with leg from link1 to link3, let it start early, so the simulation can accumulate buffer capacity
			Person person = new Person(new Id(0), "m", 35, "yes", "yes", "yes");
			Plan plan = person.createPlan(null, "yes");
			plan.createAct("h", 199.0, 0.0, link1, 0, 6*3600-500, 6*3600-500, false);
			Leg leg = plan.createLeg(1, "car", 6*3600-500, Time.UNDEFINED_TIME, Time.UNDEFINED_TIME);
			Route route = new Route();
			route.setRoute("2 3");
			leg.setRoute(route);
			plan.createAct("w", 99.0, 0.0, link3, 6*3600-390, 24*36000, Time.UNDEFINED_TIME, true);
			plans.addPerson(person);

			// add a lot of persons with legs from link2 to link3
			for (int i = 1; i <= 5000; i++) {
				person = new Person(new Id(i), "m", 35, "yes", "yes", "yes");
				plan = person.createPlan(null, "yes");
				plan.createAct("h", 99.0, 0.0, link2, 0, 7*3600 - 1801, 7*3600 - 1801, false);
				leg = plan.createLeg(1, "car", 7*3600 - 1801, Time.UNDEFINED_TIME, Time.UNDEFINED_TIME);
				route = new Route();
				route.setRoute("3");
				leg.setRoute(route);
				plan.createAct("w", 99.0, 0.0, link3, 7*3600-1790, 24*36000, Time.UNDEFINED_TIME, true);
				plans.addPerson(person);
			}
			// add a lot of persons with legs from link1 to link3
			for (int i = 5001; i <= 10000; i++) {
				person = new Person(new Id(i), "m", 35, "yes", "yes", "yes");
				plan = person.createPlan(null, "yes");
				plan.createAct("h", 99.0, 0.0, link1, 0, 7*3600 - 1902, 7*3600 - 1902, false);
				leg = plan.createLeg(1, "car", 7*3600 - 1902, Time.UNDEFINED_TIME, Time.UNDEFINED_TIME);
				route = new Route();
				route.setRoute("2 3");
				leg.setRoute(route);
				plan.createAct("w", 99.0, 0.0, link3, 7*3600-1790, 24*36000, Time.UNDEFINED_TIME, true);
				plans.addPerson(person);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		/* build events */
		Events events = new Events();
		VolumesAnalyzer vAnalyzer = new VolumesAnalyzer(3600, 9*3600, network);
		events.addHandler(vAnalyzer);

		/* run sim */
		QueueSimulation sim = new QueueSimulation(network, plans, events);
		sim.run();

		/* finish */
		int[] volume = vAnalyzer.getVolumesForLink("2");
		System.out.println("#vehicles 6-7: " + Integer.toString(volume[6]));
		System.out.println("#vehicles 7-8: " + Integer.toString(volume[7]));
		System.out.println("#vehicles 8-9: " + Integer.toString(volume[8]));

		assertEquals(3000, volume[6]); // we should have half of the maximum flow in this hour
		assertEquals(6000, volume[7]); // we should have maximum flow in this hour
		assertEquals(1000, volume[8]); // all the rest
	}
}
