/**
 * Kyle Shepard
 * APICall - once instantiated and run, will call the OneBusAway API to analyze data about the specified stop
 */
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

public class APICall implements Runnable{

    public HttpClient client;
    public HttpRequest request;
    HttpResponse<String> response;
    //CliffQueue<String[]> cQ = new CliffQueue<String[]>(5);
    //Map<String,Boolean> presentVehicles = new HashMap<String, Boolean>();
    ArrayList<String> presentVehicles = new ArrayList<String>();
    String stopNumber;
    String stopName;
    String APIKey;

    /**
     * 
     * @param uri - partial url for api call to Puget Sound OneBusAway
     * @param stopNumber - Stop number not including the "1_"
     * @param stopName - address/name for bus stop
     * @param APIKey - api key used for call
     */
    public APICall(String uri, String stopNumber, String stopName, String APIKey){

        client = HttpClient.newHttpClient();
        //test api call - remove
        request = HttpRequest.newBuilder()
        .uri(URI.create(uri  + stopNumber + ".json?key=" + APIKey)) //we want a .json file for our json.simple library
        .timeout(Duration.ofSeconds(15))
        .build();
        this.stopNumber = stopNumber;
        this.stopName = stopName;
    }

    /**
     * 
     * @param stopData: 0-stop number, 1-stop name, 2-route number, 3-on time status, 4-how late/early
     * @throws IOException
     */
    public void printEntryToCSV(String[] stopData) throws IOException {
    	FileWriter pw = new FileWriter(System.getProperty("user.dir") + "/static/stops.csv",true);
    	if(stopData.length != 0) {
    		
    		for(int i = 0; i < stopData.length - 1; i++) {
    			
    			pw.append(stopData[i] + ", ");
    			System.out.print(stopData[i] + ", ");
    		}
    		pw.append(stopData[stopData.length - 1] + "\n");
    		//System.out.println(stopData[stopData.length - 1]);
    	}
    	
    	pw.flush();
    	pw.close();
    }
    
    public void run() {
    	
    	ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(1);
    	
    	Runnable APICallThread = new Runnable() {

			@Override
			public void run() {
				
				try{
		        	//initialize HttpClient to call api call
		        	int count = 0;
		            do {
		            	if (count > 3) {
		            		break;
		            	}
		            	response = client.send(request, BodyHandlers.ofString());
		            	count++;
		            	

		            } while (response.body().contentEquals(""));	//try again if timeout
		            
		            //time variables
		            Long current;
		            Long predicted;
		            Long scheduled;
		            
		            //grab arrival information for current stop
		            JSONParser parser = new JSONParser();

		            JSONObject result = (JSONObject)parser.parse(response.body());
		            current = Long.parseLong(result.get("currentTime").toString());
		            result = (JSONObject) result.get("data");
		            result = (JSONObject) result.get("entry");
		            JSONArray arrivals = (JSONArray) result.get("arrivalsAndDepartures");
		            
		            //only continue if there are any routes returned in the first place to grab data from
		            if(arrivals.size() > 0) {
		            	
		            	int count2 = 0;
		            	//loop through each returned arrival
		            	ArrayList<String> newVehicles = new ArrayList<String>();
		            	
		            	for (int i = 0; i < arrivals.size(); i++) {
		            		
		            		//get timestamp data for each arrival to check when a bus is nearby the stop
		            		JSONObject arrivalElements = (JSONObject) arrivals.get(i);
		            		predicted = Long.parseLong(arrivalElements.get("predictedArrivalTime").toString());
		            		scheduled = Long.parseLong(arrivalElements.get("scheduledArrivalTime").toString());
		            		String vehicleID = arrivalElements.get("vehicleId").toString();
		            		String route = arrivalElements.get("routeShortName").toString();
		            		
		            		//if bus is here NOW (predicted time in minutes equals current time in minute)
		            		if((predicted - current) / 60000 == 0) {
		            			
		            			newVehicles.add(vehicleID);
		            			count2++;
		            			//keep track if bus was late/on time/ early, and by how much
		            			Long punctuality = (predicted - scheduled) / 60000;
		            			String onTimeStatus;
		            			
		            			if (punctuality == 0) {
		            				onTimeStatus = "On Time";
		            			} else if (punctuality > 0) {
		            				onTimeStatus = "Late";
		            			} else {
		            				onTimeStatus = "Early";
		            			}
		            			
		                    	// 0-stop number, 1-stop name, 2-route number, 3-on time status, 4-how late/early
		            			String[] stopData = {stopNumber, stopName, route, onTimeStatus, punctuality.toString(), vehicleID, current.toString()};
		            			
		            			//if vehicleID was not seen last time
		            			if (!presentVehicles.contains(vehicleID)) {
		            				
		            				printEntryToCSV(stopData);
		            			} else {
		            				//System.out.print("Duplicate found and deleted ");

		            			}
		            		}
		            	}
		            	presentVehicles = newVehicles;
		            	//System.out.println(stopNumber + ": "+ count2 +"/" + arrivals.size());
		            }       
		        }
		        catch (ParseException pe) {
		        	System.out.println("Tried calling api for stop# " + stopNumber + " 3 times with no server response.");
		        }
		        catch (IOException ioex) {
		        	System.out.println("Failed to write to file");
		        }
		        catch (Exception ex){
		            System.out.println("Something went wrong: " + ex.toString() + "\n");
		            //cry
		        }
			}
    	};
    	
    	scheduledPool.scheduleWithFixedDelay(APICallThread, 0, 30, TimeUnit.SECONDS);
    }
}