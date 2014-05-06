package travel.kiri.backend;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Scanner;

import travel.kiri.backend.algorithm.*;

/**
 * The class responsible for processing routing requests.
 * @author PascalAlfadian
 *
 */
public class Worker {

	public Double global_maximum_walking_distance;
	public Double global_maximum_transfer_distance;
	public Double global_multiplier_walking;
	public Double global_penalty_transfer;
	public boolean global_verbose;
	public int numberOfRequests;
	//in millis, needs to be converted to seconds later
	public long totalProcessTime;
	
	List<Track> tracks;
	Graph nodes;
	
	public Worker() throws FileNotFoundException, IOException
	{
		tracks = new ArrayList<Track>();
		nodes = new Graph();
		readConfiguration("etc/mjnserve.conf");
		readGraph("etc/tracks.conf");
		linkAngkots();
	}

	private void readConfiguration(String filename) throws FileNotFoundException, IOException {
		Properties properties = new Properties();
		properties.load(new FileInputStream(filename));
		global_maximum_walking_distance = new Double(properties.getProperty("maximum_walking_distance"));
		global_maximum_transfer_distance = new Double(properties.getProperty("maximum_transfer_distance"));
		global_multiplier_walking = new Double(properties.getProperty("multiplier_walking"));
		global_penalty_transfer = new Double(properties.getProperty("penalty_transfer"));
	}

	/**
	 * Show the summary of internal track information to the log file. Useful
	 * for debugging.
	 */
	void printTracksInfo() {
		// FIXME implement this
	}

	/**
	 * Show the internal places information (including the graph) to the log
	 * file. Useful for debugging.
	 */
	void printPlacesInfo() {
		// FIXME implement this
	}
	
	/**
	 * Compute the shortest path between the start point and the end point.
	 * @param start the start location to route
	 * @param finish the finish location to route
	 * @param customMaximumWalkingDistance the custome maximum walking distance, or null to use default
	 * @param customMultiplierWalking the custom walking multiplier, or null to use default
	 * @param customPenaltyTransfer the custom penalty transfer, or null to use default
	 * @return string containing the steps, as specified in Kalapa-Dago protocol.
	 */
	public String startComputing(LatLon start, LatLon finish, Double customMaximumWalkingDistance,
			Double customMultiplierWalking, Double customPenaltyTransfer) {
		// FIXME implement this
		// TODO rename this method to findRoute
		
		long startTime, endTime;
		
		//thread and stuff
		startTime = System.currentTimeMillis();
		int memorySize = 0;
		//if globalverbose
		
		//setting the parameters
		if(customMaximumWalkingDistance == null || customMaximumWalkingDistance==-1)
		{
			customMaximumWalkingDistance = global_maximum_walking_distance;
		}
		if(customMultiplierWalking == null || customMultiplierWalking==-1)
		{
			customMultiplierWalking = global_multiplier_walking;
		}
		if(customPenaltyTransfer == null || customPenaltyTransfer==-1)
		{
			customPenaltyTransfer = global_penalty_transfer;
		}
		
		//create virtual graph
		
		int vNodesSize = nodes.size() +2;
		int startNode = nodes.size();
		int endNode = nodes.size()+1;
		
		Graph vNodes = new Graph();
		GraphNode realNode;
		for(int i=0;i<nodes.size();i++)
		{
			realNode = nodes.get(i);
			vNodes.add(new GraphNode(realNode.getLocation(), realNode.getTrack()));
			vNodes.get(i).link(realNode);
		}
		
		//add start and finish node
		vNodes.add(new GraphNode(start, null));
		vNodes.add(new GraphNode(finish, null));

		//Link startNode to other nodes by walking
		GraphEdge mem_edge=new GraphEdge(-1,-1);
		for(int i=0;i<nodes.size();i++)
		{
			double distance = start.distanceTo(nodes.get(i).getLocation());
			if(distance <= customMaximumWalkingDistance && nodes.get(i).isTransferNode())
			{
				vNodes.get(startNode).push_back(i, customMultiplierWalking * distance);	
				memorySize+=mem_edge.getMemorySize();
			}
		}

		//Link endNode to other nodes by walking
		for(int i=0;i<nodes.size();i++)
		{
			double distance = finish.distanceTo(nodes.get(i).getLocation());
			if(distance <= customMaximumWalkingDistance && nodes.get(i).isTransferNode())
			{
				vNodes.get(i).push_back(endNode, customMultiplierWalking * distance);
				memorySize+=mem_edge.getMemorySize();
			}
		}
		
		{
			double distance = start.distanceTo(finish);
			if(distance <= customMaximumWalkingDistance)
			{
				vNodes.get(startNode).push_back(endNode, customMultiplierWalking * distance);
				vNodes.get(endNode).push_back(startNode, customMultiplierWalking * distance);
				memorySize+=2*mem_edge.getMemorySize();
			}
		}
		
		Dijkstra dijkstra = new Dijkstra(vNodes, startNode, endNode, global_verbose);
		if(global_verbose)
		{
			//prints
		}
		dijkstra.setParam(customMultiplierWalking, customPenaltyTransfer);
		dijkstra.runAlgorithm();
		
		//traversing
		int currentNode = endNode;
		
		int lastNode, angkotLength = 0;
		double distance = 0;
		StringBuilder line = new StringBuilder();
		List<String> steps = new ArrayList<String>();
		
		DecimalFormat decformat = new DecimalFormat("#.00000", new DecimalFormatSymbols(Locale.US));

		while(dijkstra.getParent(currentNode) != dijkstra.DIJKSTRA_NULLNODE)
		{
			lastNode = currentNode;
			currentNode = dijkstra.getParent(currentNode);
			
			if(lastNode == endNode || currentNode == startNode || !nodes.get(currentNode).getTrack().equals(nodes.get(lastNode).getTrack()))
			{
				if(angkotLength > 0 )
				{
					Track t = nodes.get(lastNode).getTrack();
					line.insert(0, "/");
					line.insert(0, t.getTrackId());
					line.insert(0, "/");
					line.insert(0, t.getTrackTypeId());
					
					line.append(distance);
					line.append("/");
					//places line.append(b)
					line.append("\n");
					
					steps.add(line.toString());
				}
				
				distance = (dijkstra.getDistance(lastNode) - dijkstra.getDistance(currentNode) ) / customMultiplierWalking;
				if(!(lastNode == endNode || currentNode == startNode))
				{
					distance -= customPenaltyTransfer;
				}
				
				line = new StringBuilder("walk/walk/");
				if(currentNode == startNode)
				{
					line.append("start ");
				}
				else
				{
					LatLon location = nodes.get(currentNode).getLocation();
					line.append(decformat.format(location.getLat()) +","+decformat.format(location.getLon())+" ");
				}
				
				if(lastNode == endNode)
				{
					line.append("finish");
				}
				else
				{
					LatLon location = nodes.get(lastNode).getLocation();
					line.append(decformat.format(location.getLat()) +","+decformat.format(location.getLon())+"");
				}
				
				line.append("/" + distance + "/\n");
				
				steps.add(line.toString());
				
				if(currentNode != startNode)
				{
					LatLon location = nodes.get(currentNode).getLocation();
					line = new StringBuilder(decformat.format(location.getLat()) +","+decformat.format(location.getLon())+"/");
					distance = 0;
					angkotLength = 1;
				}
			}
			else
			{
				//angkot!!
				distance += (dijkstra.getDistance(lastNode) - dijkstra.getDistance(currentNode)) / nodes.get(currentNode).getTrack().getPenalty();
				
				LatLon location = nodes.get(currentNode).getLocation();
				line = new StringBuilder(decformat.format(location.getLat()) +","+decformat.format(location.getLon())+" " + line);
				angkotLength++;
				
				//no place no code
			}
		}
		
		StringBuilder retval = new StringBuilder();
		if(steps.size()==0)
		{
			retval.append("none\n");
		}
		else
		{
			for(int i= steps.size()-1; i>=0;i--)
			{
				//FIXME append ke retval
				retval.append(steps.get(i));
			}
		}
		
		endTime = System.currentTimeMillis();
		
		//logs
		
		return retval.toString();
	}
	
	/**
	 * Find all public transports nearby the given point.
	 * @param location the location find the nearby public transports.
	 * @param double customMaximumWalkingDistance the custom maximum distance from this point, or null to use default
	 * @return string containing the nearby transport, as specified in Kalapa-Dago protocol.
	 */
	String findNearbyTransports(LatLon location,
			Double customMaximumWalkingDistance) {
		// FIXME implement this
		return null;
	}
	
	/**
	 * Toggle the verbose flag, determining whether extra information is to be printed by the workers.
	 * @return true if after the toggle, verbose is now turned on.
	 */
	boolean toggleVerbose() {
		// FIXME implement this
		global_verbose=!global_verbose;
		
		//give feedback here
		
		return global_verbose;
	}

	/**
	 * Reset the statistic values.
	 */
	void resetStatistics() {
		// FIXME implement this
		numberOfRequests = 0;
		totalProcessTime = 0;
	}

	/**
	 * Return the number of requests since last reset.
	 * @return the number of requests
	 */
	int getNumberOfRequests() {
		// FIXME implement this
		return numberOfRequests;
	}

	/**
	 * Return the total processing time for all requests.
	 * @return the total process time.
	 */
	double getTotalProcessTime() {
		// FIXME implement this
		//converts the time to seconds
		return totalProcessTime/1000.0;
	}
	
	public boolean readGraph(String filename)
	{
		try {
			Scanner linescan = new Scanner(new File(filename));
			Scanner scan;			
			String line, word;
			while(linescan.hasNextLine())
			{
				line = linescan.nextLine();
				//kalau ada karakter dan bukan comment
				if(line.length()>0 && line.charAt(0)!='#')
				{
					scan = new Scanner(line);
					
					//trackid
					word = scan.next();
					Track track = new Track(word);
					
					//penalty
					word = scan.next();
					track.setPenalty(Double.parseDouble(word));
					
					//number of nodes
					int numOfNodes = scan.nextInt();
					
					//foreach nodes
					for(int i=0;i<numOfNodes;i++)
					{
						int nodeIndex = nodes.size();
						//parse lat and lon
						double lat = Double.parseDouble(scan.next());
						double lon = Double.parseDouble(scan.next());
						GraphNode node = new GraphNode(new LatLon(lat, lon), track);
						track.addNode(node);
						nodes.add(node);
						
						//connect it with the previous node
						if(i>0)
						{
							double distance = node.getLocation().distanceTo(track.getNode(i-1).getLocation());
							nodes.get(nodeIndex-1).push_back(nodeIndex, distance);
						}
					}
					
					//loop
					int loop = scan.nextInt();
					
					//if loop
					if(loop>0)
					{
						//connect last node to the first
						GraphNode first = track.getNode(0);
						GraphNode last = track.getNode(numOfNodes-1);
						
						int firstIndex = nodes.size()-track.getSize();
						int lastIndex = nodes.size()-1;
						
						double distance = first.getLocation().distanceTo(last.getLocation());
						//pushback edge
						nodes.get(lastIndex).push_back(firstIndex, distance);
					}
					
					//transferNodes
					word = scan.next();
					String[] tnodes = word.split(",");
					//nodes
					int till, start, finish;
					for(int i=0;i<tnodes.length;i++)
					{
						//if a-b
						if((till=tnodes[i].indexOf("-"))!=-1)
						{
							String[] sf = tnodes[i].split("-");
							start = Integer.parseInt(sf[0]);
							finish = Integer.parseInt(sf[1]);
						}
						else
						{
							start = Integer.parseInt(tnodes[i]);
							finish = start;
						}
						
						for(int j=start;j<=finish;j++)
						{
							track.getNode(j).setTransferNode(true);
						}
					}
					
					tracks.add(track);
					
				}
			}
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
		
		
		return true;
	}
	
	void linkAngkots()
	{
		int link = 0;
		for(int i=0;i<nodes.size();i++)
		{
			for(int j=i+1; j<nodes.size();j++)
			{
				//if not in same track and both are transferNode
				if(!(nodes.get(i).getTrack().equals(nodes.get(j).getTrack())) && nodes.get(i).isTransferNode() && nodes.get(j).isTransferNode())
				{
					double distance = nodes.get(i).getLocation().distanceTo(nodes.get(j).getLocation());
					if(distance < global_maximum_transfer_distance)
					{
						nodes.get(i).push_back(j, distance);
						nodes.get(j).push_back(i, distance);
						link++;
					}
				}
			}
		}
	}
	
	public String toString()
	{
		String t = "";
		for(Track tr : tracks)
		{
			t+=tr+"\n";
		}
		return t;		
	}

}
