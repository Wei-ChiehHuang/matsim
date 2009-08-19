package playground.mmoyo.precalculation;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.matsim.api.basic.v01.Id;
import org.matsim.transitSchedule.api.TransitSchedule;
import org.matsim.transitSchedule.api.TransitLine;
import org.matsim.transitSchedule.api.TransitRoute;
import org.matsim.transitSchedule.api.Departure;
import org.matsim.transitSchedule.api.TransitRouteStop;
import org.matsim.transitSchedule.api.TransitStopFacility;

/**reports the departures of transitStopFacilities without logic layer*/
public class PlainTimeTables {
	TransitSchedule transitSchedule;
	Map <Id, double[]> transitRouteDepartureMap = new TreeMap <Id, double[]>();
	
	public PlainTimeTables (final TransitSchedule transitSchedule){
		this.transitSchedule = transitSchedule;
		setDepartures();
	}
	
	/**creates array of departures for every transitRoute*/
	private void setDepartures(){
		for (TransitLine transitLine : 	transitSchedule.getTransitLines().values()){
			for (TransitRoute transitRoute: transitLine.getRoutes().values()){
				double[] departureArray = new double[transitRoute.getDepartures().size()];
				int i=0;
				for (Departure departure: transitRoute.getDepartures().values()){
					departureArray[i++]= departure.getDepartureTime();
				}
				Arrays.sort(departureArray);
				transitRouteDepartureMap.put(transitRoute.getId(), departureArray);
			}
		}
	}

	
	public double getNextDeparture(final TransitRoute transitRoute, final TransitStopFacility transitStopFacility, double time){
		double[] departureArray = transitRouteDepartureMap.get(transitRoute.getId());
		TransitRouteStop transitRouteStop =	transitRoute.getStop(transitStopFacility);
		double ArrivalOffset = transitRouteStop.getArrivalOffset();
		
		time = time - ArrivalOffset;  

		int length = departureArray.length;
		int index =  Arrays.binarySearch(departureArray, time);
		if (index<0){
			index = -index;
			if (index <= length)index--; else index=0;	
		}else{
			if (index < (length-1))index++; else index=0;	
		}
		return departureArray[index]+ ArrivalOffset;
	}

}
