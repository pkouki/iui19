/*
\ * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.umd.cs.example;

import java.text.DecimalFormat;


//org.linqs.psl.evaluation.result.memory.MemoryFullInferenceResult

import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxPseudoLikelihood;
import org.linqs.psl.config.*
import org.linqs.psl.database.DataStore
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabasePopulator;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.database.rdbms.RDBMSDataStore
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import org.linqs.psl.utils.evaluation.*;
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.model.term.*;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.database.Queries;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.model.kernel.*;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.application.inference.result.memory.MemoryFullInferenceResult;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import java.security.*;

import java.util.Set;

import javax.lang.model.util.Elements;

import com.google.common.collect.TreeMultimap;
import com.google.common.collect.Ordering;

import java.nio.file.Paths;
import java.nio.file.Files;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.Runtime;
import java.lang.InterruptedException;



//interface declaration for the communication between psl.jar and the server	
public interface PSLFinishedEventDebug
{
    public void pslFinishedEventDebug( String userJSONData );
}


public class LastFMFinalDebug {

    protected PSLFinishedEventDebug _pfe;

    public LastFMFinalDebug(PSLFinishedEventDebug pfe) {
        this._pfe = pfe
    }

    public doit(String userID, String HOME_DIR) {
		
        //keep track of the time
        //println "START Time is " + new Date();

		//directory for simos
		//String HOME_DIR = "/home/tmp/PSLProjects/kouki-recsys15/last.fm-mturk";
		
		//directory for moss
		//String HOME_DIR = "/scratch/pkouki/kouki-recsys15/last.fm-mturk";
		
		//directory for AWS server
		//String HOME_DIR = "/home/pkouki/kouki-recsys15/last.fm-mturk";

		//directory for fog
		//String HOME_DIR = "/home/pkouki/kouki-recsys15/last.fm-mturk";
		
		String fileNameMD5 = MD5(userID + ".user");
		//println "MD5 of string " + userID + " is " + fileNameMD5;
		
		//first create a file which will contain only one user: the userID
		def crawlerFile = createFile(HOME_DIR + java.io.File.separator + fileNameMD5);
		crawlerFile.append(userID+"\n");
		
		String CMD = "./one_user.sh " + fileNameMD5 + " " + HOME_DIR;	
		//String CMD = "./one_user.sh 1user.db"
		
        String user_md5;
        //user_md5 = "71bbb17fd7986a59e9482a25678fc025"
        //user_md5 = "0f152dc5ba0129c8b324806c91b1487b" //initialize it for now 
        //user_md5 = "612712c06fd3d358cbba0aa7e2ee393a"
		//user_md5 = "926374f324a21554cd16074f96dc60b7"
		//user_md5 = "29e6f8a61f9e51585da8167b5db880a2"	//lolita_h
		//user_md5 = "10026ff76cd3d3bc73822ca9d442362a";
		//user_md5 = "2af1648c988622bbaf1fb4d623cf2ae1";
		//user_md5 = "7bee6a0c30afc66ff43877415f87ce38"; //for user leandronogueira
		//user_md5 = "c59bff8ebb28ce3ea729c18d6a3cd6e2"; //for user JeanetteStar
		user_md5 = "2af1648c988622bbaf1fb4d623cf2ae1"; //problematic user
		
		boolean callCrawler = false;

		Integer TAGSTOKEEP = 5;//5;
		Integer LIMIT = 50;	//the number of items to keep for each user is 50
		Integer NUM_OF_ITEMS_PER_QUESTION = 3; //the number of distinct items we need to create explanations for
		Integer NUM_OF_ITEMS_TO_RATE = 3;
		Integer MINSIZEOFFRIENDS = 1;
		Integer MINSIZEOFARTISTS = 1;
		Integer THRESHOLD = 3	//how many instances per explanation styles 
								//(e.g. how many similar friends, how many different items) we will show to the users
		
		
        //call the crawler to create the directories
        if(callCrawler){
			user_md5 = crawlData(CMD, HOME_DIR);
        }
        //done with the crawler - now run the PSL model 

        ConfigManager cm = ConfigManager.getManager()
        ConfigBundle config = cm.getBundle("basic-example")
        
        def defaultPath = System.getProperty("java.io.tmpdir")
        String dbpath = config.getString("dbpath", defaultPath + File.separator + "rec_sys_lastfm")
        
        //create the DB using as argument the md5
        //println "Use H2"
        //DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)
        //println "Use Postgres"
        DataStore data = new RDBMSDataStore(new PostgreSQLDriver(user_md5, true), config);
        //DataStore data = new RDBMSDataStore(new PostgreSQLDriver("pkouki", true), config);
        
        //this structure stores entries of the form <sorted_value_of_rating,item> for the user that we run the script
        //for each user we keep at most LIMIT items with the highest values
        final Comparator<Double> DECREASING_DOUBLE_COMPARATOR = Ordering.<Double>natural().reverse().nullsFirst();
        TreeMultimap<Double, String> rating_item = new TreeMultimap<>(DECREASING_DOUBLE_COMPARATOR, Ordering.natural());
        //this structure stores entries of the form <item,<full_grounding>> 
        //for each item we store a set of all the groundings that were generated
        HashMap<String, HashSet<String>> item_grounding = new HashMap();
        
                
        //data directories
        //def userDir = 'last.fm-mturk' + java.io.File.separator + user_md5 + java.io.File.separator;
        //def outDir = "userOutput" + java.io.File.separator;

		def userDir = HOME_DIR + java.io.File.separator + user_md5 + java.io.File.separator;
		//def outDir = HOME_DIR + java.io.File.separator + "userOutput" + java.io.File.separator; //+ "userOutput" + java.io.File.separator;
		//new File(outDir).mkdir();
		
		//def userDir = outDir + java.io.File.separator + user_md5
		//new File(userDir).mkdir();	//in this directory we will store the output data for this user

		
        //before doing anything else just load the files which map the user unique ids and the artist uniques ids
        String user_uniqueID_name = "userUniqueIDs"
        String item_uniqueID_name = "artistUniqueIDs"
        String tag_uniqueID_name = "tagUniqueIDs"
        String artists_listeners_playcount = "top_1000_artists"
        
        //println "About to load the maps"
        //The following structures are used to map unique ids to strings
        //each entry is of the form <UniquID,UserName>, e.g. <123, John>
        HashMap<String, String> userIDsToNames = new HashMap();
		HashMap<String, String> itemIDsToNames = new HashMap();
        HashMap<String, String> tagIDsToNames = new HashMap();
        //HashMap<String, String> artists_stats = new HashMap();
		
		//The following are the reverse of the above structures and are used to map strings to unique ids
		//each entry is of the form <UserName, UserID>, e.g. <John,123>
		HashMap<String, String> namesToUserIDs = new HashMap();
		HashMap<String, String> namesToItemIDs = new HashMap(); 
		HashMap<String, String> namesToTagIDs = new HashMap();
		
		//the following structure maps the changed artist's name (i.e., without the ( or . or , with the one that appears in the last.fm database)
		//this strutucre will be used to crawl last.fm later to get the url and image for this particular artist
		HashMap<String,String> cleanArtistNameToRealArtistName = new HashMap();
		
		loadData(userDir+user_uniqueID_name, userIDsToNames, namesToUserIDs)
		loadArtistsData(userDir+item_uniqueID_name, itemIDsToNames, namesToItemIDs, cleanArtistNameToRealArtistName)
		loadTags(userDir+tag_uniqueID_name, tagIDsToNames, namesToTagIDs, TAGSTOKEEP)
		
//		for(String key : namesToTagIDs.keySet()){
//			println "key " + key + " value " + namesToTagIDs.get(key);
//		}
		
		HashMap<String, String> artists_stats = loadTopArtists(userDir+artists_listeners_playcount)
		
		//println "Done with loading the maps"
        
		//now load the files with the similarities
		//each file will be of the form "item_item \t similarity_value"
		//println "About to load the similarity files"
		HashMap<String, Double> user_user_sim = new HashMap();
		HashMap<String, Double> artist_artist_lastfmsim = new HashMap();
		HashMap<String, Double> artist_artist_tagsim = new HashMap();
		HashMap<String, Double> artist_artist_CFsim = new HashMap();
		HashMap<String, Double> friend_friend_sim = new HashMap();
		HashMap<String, Double> tag_popularity = new HashMap();
		
		loadSimilarities(user_user_sim, userDir+"inputuser-user.artist.jaccard.top20");
		//println "size of structure user_user_sim " + user_user_sim.size();
		
		loadSimilarities(user_user_sim, userDir+"inputuser-user.artist.cosine.top20");//add again the same function to load the cosine
		//println "size of structure user_user_sim " + user_user_sim.size();
		
		loadSimilarities(artist_artist_lastfmsim, userDir+"artist-artist.lastfm.top20");
		//println "size of structure artist_artist_lastfmsim " + artist_artist_lastfmsim.size();
		
		loadSimilarities(artist_artist_tagsim, userDir+"artist-artist.tags.jaccard.top20");
		//println "size of structure artist_artist_tagsim " + artist_artist_tagsim.size();
		
		loadSimilarities(artist_artist_CFsim, userDir+"artist-artist.user.jaccard.top20");
		loadSimilarities(friend_friend_sim, userDir+"inputfriends-user-user.artist.cosine");
		loadSimilarities(friend_friend_sim, userDir+"inputfriends-user-user.artist.jaccard");
		
		
		HashSet<String> offensiveTags = new HashSet();
		fillInOffensiveTags(offensiveTags);	//fills in the structure with the offensive tags
		
		loadTopTags(tag_popularity, offensiveTags, userDir+"top_1000_tags.noid");
		
		
		//println "Done with loading the similarity files"
		
		
        //print the above structures to make sure everything is stored properly
        //println "About to print the userIDsToNames"
        //printIDsToNames(userIDsToNames)
        //println "About to print the itemIDsToNames"
        //printIDsToNames(itemIDsToNames)
        //println "About to print the tagIDsToNames"
        //printIDsToNames(tagIDsToNames)
        
        //sq = true;	//when sq=false then the potentials are linear while when sq is true the potentials are squared
        int partitionID = 0;	//this id is for creating partitions with unique ids each time
        
		//create the PSL model 
		PSLModel m = createModel(this, data);
		
		//println m;

	    //we put in the same partition things that are observed
	    Partition evidencePartition = data.getPartition((partitionID++).toString());  // this is a common partition shared between the WL and the inference 
	    Partition targetPartition = data.getPartition((partitionID++).toString());
	
		
		//load the data to the predicates
		String output = loadData(evidencePartition, targetPartition, data, userDir, MINSIZEOFFRIENDS, MINSIZEOFARTISTS);
		
		if(!output.equals("")) { //in case the user has not enough info in his profile
			//println "Java callback"
			if (null != _pfe) {
				_pfe.pslFinishedEventDebug(output);
			}
			return;
		}
		
	    //target partition
	    Database db1 = data.getDatabase(targetPartition, [rated, ratedTrain, 
		    sim_lastfm_items, sim_jaccard_items, sim_tags_items,
		    sim_cosine_users, sim_jaccard_users,
		    users_are_friends, 
		    item_has_tag, item_is_popular] as Set, evidencePartition);
	
	
	    //println "About to perform inference - Time " + new Date();
	
	    //run MPE inference
	    //------------- start
	    MPEInference inferenceApp = new MPEInference(m, db1, config);
	    MemoryFullInferenceResult inf_result = inferenceApp.mpeInference();
	    //call the getGroundRuleStore which will enable us to get access to the ground rules
	    GroundRuleStore groundRuleStore = inferenceApp.getGroundRuleStore();
	    //to print the doubles with 3 decimal digits
	    DecimalFormat df3 = new DecimalFormat(".###");
	
		//generate the recommendations
		generateRecommendations(groundRuleStore, userIDsToNames, itemIDsToNames, tagIDsToNames, rating_item, item_grounding, LIMIT);
		
	    //print some stats for incompatibility and infeasibility
//		if(inf_result.getTotalWeightedIncompatibility()!=null)
//		    println "[DEBUG inference]: Incompatibility = " + df3.format(inf_result.getTotalWeightedIncompatibility())
//	    if(inf_result.getInfeasibilityNorm()!=null)
//		    println "[DEBUG inference]: Infeasibility = " + df3.format(inf_result.getInfeasibilityNorm())
	
	    inferenceApp.close();

	    //after updating the structures then we can print those 
	    //printRecommendations(rating_item, item_grounding, cleanArtistNameToRealArtistName, HOME_DIR);
	
		//here we define the structures where we will keep the items along with the explanations
		TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings = new TreeMultimap<>(DECREASING_DOUBLE_COMPARATOR, Ordering.arbitrary());
		
		//this structure keeps for each item the textual content - stores only 3 styles
		HashMap<String,String> item_textual = new HashMap();
		//this structure keeps for each item the json content for collaspible trees - stores only 3 styles
		HashMap<String,String> item_jsonCollapsible = new HashMap();
		//this structure keeps for each item the json content for Venn diagrams - stores only 3 styles
		HashMap<String,String> item_jsonVenn = new HashMap();
		//this structure keeps for each item the json content for cluster dendrograms - stores only 3 styles
		HashMap<String,String> item_jsonCluster = new HashMap();
		//this structure keeps for each item the json content for radial dendrograms - stores only 3 styles
		HashMap<String,String> item_jsonRadial = new HashMap();
		
	    //generate the explanations
		generateExplanationsForUser(userID, user_md5, rating_item, item_grounding, artists_stats, THRESHOLD, 
			/*outDir,*/ rating_item_groundings, item_textual, item_jsonCollapsible, item_jsonVenn, item_jsonCluster, 
			item_jsonRadial, offensiveTags, 
			user_user_sim, artist_artist_lastfmsim, artist_artist_tagsim, artist_artist_CFsim, friend_friend_sim, tag_popularity,
			namesToUserIDs, namesToItemIDs, namesToTagIDs);
			
		//in these structures we will put the items for q1, q2, q3, and q4
		//each key is of the form item_____predictedRating_____numberOfGroundings, e.g. Andrew Bird_____0.698_____5
		HashMap<String, HashSet<String>> q1Items = new HashMap();	//these are for the question that ask the two questions about perceived transparency and attitude confidence
		HashMap<String, HashSet<String>> q2Items = new HashMap();	//textual vs visual
		HashMap<String, HashSet<String>> q3Items = new HashMap();	//these are for the ranking questions 
		HashMap<String, HashSet<String>> q4Items = new HashMap();	//rating questions
		HashMap<String, HashSet<String>> qVennItems = new HashMap();	//Venn diagram questions
		
		//here we generate the structures needed to create the string output
		selectItemsForQuestions(rating_item_groundings, NUM_OF_ITEMS_PER_QUESTION, NUM_OF_ITEMS_TO_RATE, q1Items, q2Items,q3Items, q4Items, qVennItems);
		
		//now given the q$iItems we just need to create the json objects to write to the string
		String jSonOutput = createJsonOutput(q1Items, q2Items,q3Items, q4Items, qVennItems, item_textual, item_jsonCollapsible, item_jsonVenn, item_jsonCluster, item_jsonRadial, cleanArtistNameToRealArtistName, HOME_DIR)
		
		
	    //println "Done with the inference - Time " + new Date();
	
	    //call the garbage collector - just in case!
	    System.gc();
	
	    db1.close();
	
	    //delete the DB
		if(callCrawler){
			deleteDataBase(CMD, user_md5)
		}
	
		println "output = " + jSonOutput
		
	    //Java callback
	    //println "Java callback"
        if (null != _pfe) {
	      _pfe.pslFinishedEventDebug(jSonOutput);
        }
	
	    //println "END Time is " + new Date();
                                

    }


void fillInOffensiveTags(HashSet<String> offensiveTags){

	offensiveTags.add("Officially Shit")
	offensiveTags.add("Trash")
	offensiveTags.add("Crap")
	offensiveTags.add("trash metal")
	offensiveTags.add("boring")
	offensiveTags.add("mistagged")
	offensiveTags.add("f")
	offensiveTags.add("nazi")
	offensiveTags.add("wtf")
	offensiveTags.add("garbage")
	offensiveTags.add("stupid")
	offensiveTags.add("Gay Metal")
	offensiveTags.add("silly")
	offensiveTags.add("gay")
	
}
	
public String MD5(String md5) {
	try {
		 java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
		 byte[] array = md.digest(md5.getBytes());
		 StringBuffer sb = new StringBuffer();
		 for (int i = 0; i < array.length; ++i) {
		   sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1,3));
		}
		 return sb.toString();
	 } catch (java.security.NoSuchAlgorithmException e) {
	 }
	 return null;
 }
	 

//given the structures q$iItems this function creates the json file	
String	createJsonOutput(HashMap<String, HashSet<String>> q1Items, 
						 HashMap<String, HashSet<String>> q2Items, 
						 HashMap<String, HashSet<String>> q3Items, 
						 HashMap<String, HashSet<String>> q4Items,
						 HashMap<String, HashSet<String>> qVennItems,
						 HashMap<String,String> item_textual,
						 HashMap<String,String> item_jsonCollapsible,
						 HashMap<String,String> item_jsonVenn,
						 HashMap<String,String> item_jsonCluster,
						 HashMap<String,String> item_jsonRadial,
						 HashMap<String,String> cleanArtistNameToRealArtistName,
						 String HOME_DIR){
	
	String output = "[";					 
	//iterate over the q1Items structure
	//for each item in this structure we will create two json objects: one for the question that we will 
	//show the recommendation only and we will ask quality questions
	//and one that we will show the item along with the explanation and ask questions
	int item_counter=1;
	String type;
	
	//create questions for Q1 (transparency and attitude)
	for(String item_rating_numgroundings : q1Items.keySet()){
		String item = item_rating_numgroundings.split("_____")[0];
		String predicted_rating = item_rating_numgroundings.split("_____")[1];
		String number_of_groundings = item_rating_numgroundings.split("_____")[2];
		
		String url_image = generateURLAndImage(item, cleanArtistNameToRealArtistName, HOME_DIR)
		
		//question item 1 : item without explanations
		type = "quality"
		output += "[" + createJsonQuality(type, item_counter, item, predicted_rating, number_of_groundings, url_image) + "],\n";
		
		//question item 2 : item with explanation
		HashSet<String> groundings = q1Items.get(item_rating_numgroundings);
		type = "style";
		output += "[" + createJsonRanking(type, item_counter, item, predicted_rating, number_of_groundings, groundings, url_image) + "],\n";
		item_counter++;
	}
	
	int counter=1;
	//create questions for Q3 - the ranking
	for(String item_rating_numgroundings : q3Items.keySet()){
		String item = item_rating_numgroundings.split("_____")[0];
		String predicted_rating = item_rating_numgroundings.split("_____")[1];
		String number_of_groundings = item_rating_numgroundings.split("_____")[2];
		
		String url_image = generateURLAndImage(item, cleanArtistNameToRealArtistName, HOME_DIR)
		
		//question item 2 : item with explanation
		HashSet<String> groundings = q3Items.get(item_rating_numgroundings);
		type = "quality";		
		output += "[" + createJsonQuality(type, item_counter, item, predicted_rating, number_of_groundings, url_image) + "],\n";
		
		type = "ranking"
		if(counter==q3Items.size())//for the last element do not put a comma at the end
			output += "[" + createJsonRanking(type, item_counter, item, predicted_rating, number_of_groundings, groundings, url_image) + "],\n";
		else
			output += "[" + createJsonRanking(type, item_counter, item, predicted_rating, number_of_groundings, groundings, url_image) + "],\n";
		item_counter++;
		counter++;
	}
	
	counter=1;
	//create questions for Q Venn (textual and visual)
	for(String item_rating_numgroundings : qVennItems.keySet()){
		String item = item_rating_numgroundings.split("_____")[0];
		String predicted_rating = item_rating_numgroundings.split("_____")[1];
		String number_of_groundings = item_rating_numgroundings.split("_____")[2];
		
		String url_image = generateURLAndImage(item, cleanArtistNameToRealArtistName, HOME_DIR)
		
		type = "quality";
		output += "[" + createJsonQuality(type, item_counter, item, predicted_rating, number_of_groundings, url_image) + "],\n";
		
		HashSet<String> groundings = qVennItems.get(item_rating_numgroundings);
		println "\nFor item " + item + " groundings are "
		int i=1;
		for(String grounding : groundings){
			println i + "." + grounding;
			i++;
		}
		
		//textual explanation
		type = "textexplanation";
		//output += "[" + createTextualExplanation(type, item_counter, item, predicted_rating, number_of_groundings, groundings) + "],\n";
		output += "[" + createTextualExplanation(type, item_counter, item, predicted_rating, number_of_groundings, item_textual, url_image) + "],\n";
		
		//collapsible tree
		type = "collapsibletree";
		output += "[" + createJsonCollapsibleTree(type, item_counter, item, predicted_rating, number_of_groundings, groundings, item_jsonCollapsible, url_image) + "],\n";

		//Venn diagrams
		type = "venndiagram";
		output += "[" + createJsonCollapsibleTree(type, item_counter, item, predicted_rating, number_of_groundings, groundings, item_jsonVenn, url_image) + "],\n";

		//cluster dendrogram
		type = "clusterdendrogram";
		output += "[" + createJsonClusterDendrogram(type, item_counter, item, predicted_rating, number_of_groundings, groundings, item_jsonCluster, url_image) + "],\n";
		
		//radial dendrogram
		type = "radialdendrogram";
		
		if(counter==qVennItems.size())
			output += "[" + createJsonRadialDendrogram(type, item_counter, item, predicted_rating, number_of_groundings, groundings, item_jsonCluster, url_image) + "]\n";
		else
			output += "[" + createJsonRadialDendrogram(type, item_counter, item, predicted_rating, number_of_groundings, groundings, item_jsonCluster, url_image) + "],\n";
		
		item_counter++;
		
		counter++;
	}

	output += "]";
	//println "output = \n" + output 					 					 
	return output;
	
}

//this function given the artist id, calls a python script and get the url to the last.fm page and the last.fm image of this artist					 
String generateURLAndImage(String item, HashMap<String, String> cleanArtistNameToRealArtistName, String HOME_DIR){
	String url_image;
	
	String realItem = "";
	//first check if this item is contained in the structure cleanArtistNameToRealArtistName
	if(!cleanArtistNameToRealArtistName.containsKey(item)){
		println "[ERROR]: Item " + item + " does not exist in the structure cleanArtistNameToRealArtistName";
		return null;
	}
	else{
		try {
			realItem = cleanArtistNameToRealArtistName.get(item);
			String CMD = "./get_artist_image.py " + realItem;
	
			Process p = Runtime.getRuntime().exec(CMD, null, new File(HOME_DIR)); p.waitFor();
	
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line=reader.readLine();
		
			String user_md5;
			while (line != null) {
				System.out.println(line);
				if(!line.contains("NULL")){
					String[] tokens = line.split("\t");
					//here implement the code that retrieves the last.fm url and the image url
					if(line.startsWith("URL")) {
						url_image = tokens[1];
					}
					else if(line.startsWith("IMG")){
						url_image += "\t" + tokens[1];
					}
					line = reader.readLine();
				}
				else{
					println "[ERROR] in generateURLAndImage: The item " + realItem + " does not exist in the last.fm database"
					return null;
				}
			}
			//println "url_image = " + url_image
			return url_image;
		}
		catch(IOException e1) {
			println "IOException when calling the crawler."
			org.codehaus.groovy.runtime.StackTraceUtils.sanitize(new Exception(e1)).printStackTrace();
			return null;
		}
		catch(InterruptedException e2) {
			println "InterruptedException when calling the crawler."
			org.codehaus.groovy.runtime.StackTraceUtils.sanitize(new Exception(e2)).printStackTrace();
			return null;
		}
	}
	
	
}						 
						 
String createTextualExplanation(String type, int item_counter, String item, String predicted_rating, 
	String number_of_groundings, HashSet<String> groundings, String url_image){
	String output;
	
	output = "{\"type\":\"i" + item_counter.toString() + "-" + type + "\",";	//type
	output += "\"item\":\"" + item + "\",";		 //item
	output += "\"lastfm-url\":\"" + url_image.split("\t")[0] + "\",";		 //url
	output += "\"lastfm-image\":\"" + url_image.split("\t")[1] + "\",";		 //image
	output += "\"data\":\"We recommend to you the artist <bold>" + item + "</bold> because:\\\\n"	//data
	
	int counter_style=1;
	//styles
	for(String grounding : groundings){
		//println "-----Textual " + grounding;
		if(counter_style == groundings.size())//if this is the last grounding then you need to close the "
			output +=  counter_style.toString() + "." + grounding.replace("\n", "\\\\n") + "\","
		else
			output +=  counter_style.toString() + "." + grounding.replace("\n", "\\\\n") 
		
		counter_style++;
	}
	
	output += "\"rating\":\"" + predicted_rating + "\",";		 //rating
	output += "\"numOfStyles\":\"" + number_of_groundings + "\"}"//num of styles

	
	return output;
}						 

String createTextualExplanation(String type, int item_counter, String item, String predicted_rating, 
	String number_of_groundings, HashMap<String,String> item_textual, String url_image){
	String output;
	
	output = "{\"type\":\"i" + item_counter.toString() + "-" + type + "\",";	//type
	//output += "\"data\":\"We recommend to you the artist <bold>" + item + "</bold> because:\\n"	//data
	output += "\"item\":\"" + item + "\",";		 //item
	output += "\"lastfm-url\":\"" + url_image.split("\t")[0] + "\",";		 //url
	output += "\"lastfm-image\":\"" + url_image.split("\t")[1] + "\",";		 //image
	output += "\"data\":\"";//We recommend to you the artist <bold>" + item + "</bold> because:\\n"	//data
	
//	int counter_style=1;
//	//styles
//	for(String grounding : groundings){
//		//println "-----Textual " + grounding;
//		if(counter_style == groundings.size())//if this is the last grounding then you need to close the "
//			output +=  counter_style.toString() + "." + grounding.replace("\n", "\\n") + "\","
//		else
//			output +=  counter_style.toString() + "." + grounding.replace("\n", "\\n")
//		
//		counter_style++;
//	}
	
	println "TextualExplanation: For item " + item + " groudnigns are "
	println item_textual.get(item);
	
	output += item_textual.get(item);
	
	output += "\",\"rating\":\"" + predicted_rating + "\",";		 //rating
	output += "\"numOfStyles\":\"" + number_of_groundings + "\"}"//num of styles

	
	return output;
}


String createJsonCollapsibleTree(String type, int item_counter, String item, String predicted_rating, 
	String number_of_groundings, HashSet<String> groundings, HashMap<String,String> item_jsonCollapsible, String url_image){
	String output;
	
//	output = "{\"type\":\"i" + item_counter.toString() + "-" + type + "\",";	//type
//	output += "\"item\":\"" + item + "\",";		 //item
//	output += "\"lastfm-url\":\"" + url_image.split("\t")[0] + "\",";		 //url
//	output += "\"lastfm-image\":\"" + url_image.split("\t")[1] + "\",";		 //image
//	output += "\"data\":\"";//We recommend to you the artist <bold>" + item + "</bold> because:\\n"	//data
	
	//output = "{";
	output = "";
	
	if(!item_jsonCollapsible.containsKey(item)){
		System.out.println("[ERROR]: in createJsonCollapsibleTree - the item " + item + " does not exist");
		return;
	}
	else{
		output += item_jsonCollapsible.get(item);
	}
	
//	output += "\"}";
	
//	output += ",\"rating\":\"" + predicted_rating + "\",";		 //rating
//	output += "\"numOfStyles\":\"" + number_of_groundings + "\"}"//num of styles

	return output;

}

String createJsonClusterDendrogram(String type, int item_counter, String item, String predicted_rating, String number_of_groundings, 
	HashSet<String> groundings, HashMap<String,String> item_jsonCluster, String url_image){
	String output = "";	//initialize the output
	if(!item_jsonCluster.containsKey(item)){
		System.out.println("[ERROR]: in createJsonClusterDendrogram - the item " + item + " does not exist");
		return;
	}
	else{
		
//		output = "{\"type\":\"i" + item_counter.toString() + "-" + type + "\",";	//type
//		output += "\"item\":\"" + item + "\",";		 //item
//		output += "\"lastfm-url\":\"" + url_image.split("\t")[0] + "\",";		 //url
//		output += "\"lastfm-image\":\"" + url_image.split("\t")[1] + "\",";		 //image
//		output += "\"data\":\"";//We recommend to you the artist <bold>" + item + "</bold> because:\\n"	//data
		
		output = "";
		
		String[] csvContents = item_jsonCluster.get(item).split("\n");	//here we store the csv content and we will transform it to json
		//in the csvContents[0] we store the title "id,value" which we do not need here
		int i=0;
		for(String csvLine : csvContents){
			if(i==(csvContents.length-1)){//if this is the last element then we need to close the json object 
				output += "{\"id\":\"" + csvLine + "\", \"value\":\"undefined\"}";
			}
			else if(i!=0){	//for all elements apart from the first one
				output += "{\"id\":\"" + csvLine + "\", \"value\":\"undefined\"},";
			}
			i++;
		}
		//output += ",\"rating\":\"" + predicted_rating + "\",";		 //rating
		//output += "\"numOfStyles\":\"" + number_of_groundings + "\"}"//num of styles
		
	}

	return output;

}

String createJsonRadialDendrogram(String type, int item_counter, String item, String predicted_rating, String number_of_groundings, 
	HashSet<String> groundings, HashMap<String,String> item_jsonRadial, String url_image){
	
	String output = "";	//initialize the output
	if(!item_jsonRadial.containsKey(item)){
		System.out.println("[ERROR]: in createJsonClusterDendrogram - the item " + item + " does not exist");
		return;
	}
	else{
		
//		output = "{\"type\":\"i" + item_counter.toString() + "-" + type + "\",";	//type
//		output += "\"item\":\"" + item + "\",";		 //item
//		output += "\"lastfm-url\":\"" + url_image.split("\t")[0] + "\",";		 //url
//		output += "\"lastfm-image\":\"" + url_image.split("\t")[1] + "\",";		 //image
//		output += "\"data\":\"";//We recommend to you the artist <bold>" + item + "</bold> because:\\n"	//data
		
		String[] csvContents = item_jsonRadial.get(item).split("\n");	//here we store the csv content and we will transform it to json
		//in the csvContents[0] we store the title "id,value" which we do not need here
		int i=0;
		for(String csvLine : csvContents){
			if(i==(csvContents.length-1)){//if this is the last element then we need to close the json object 
				output += "{\"id\":\"" + csvLine + "\"}";
			}
			else if(i!=0){	//for all elements apart from the first one
				output += "{\"id\":\"" + csvLine + "\"},";
			}
			i++;
		}
//		output += ",\"rating\":\"" + predicted_rating + "\",";		 //rating
//		output += "\"numOfStyles\":\"" + number_of_groundings + "\"}"//num of styles
	}
	return output;

}

//each entry should be of the form
//{"type":"i1-quality","data":"We recommend to you the artist <bold>Foals</bold>","rating":"0.64","numOfStyles":"7"}						
String createJsonQuality(String type, int item_counter, String item, String predicted_rating, 
						String number_of_groundings, String url_image){
	String output;
	
	output = "{\"type\":\"i" + item_counter.toString() + "-" + type + "\",";	//type
	output += "\"item\":\"" + item + "\",";		 //item
	output += "\"lastfm-url\":\"" + url_image.split("\t")[0] + "\",";		 //url
	output += "\"lastfm-image\":\"" + url_image.split("\t")[1] + "\",";		 //image
	output += "\"data\":\"We recommend to you the artist <bold>" + item + "</bold>\","	//data
	output += "\"rating\":\"" + predicted_rating + "\",";		 //rating
	output += "\"numOfStyles\":\"" + number_of_groundings + "\"}"//num of styles
	
	return output;
}											 
						 
						 
//each entry should be of the form 
/*{"type":"i1-style", 
"data":"We recommend to you the artist The Doors because:",
"style1" : "The Doors has similar tags with the artists: [Pink Floyd, David Gilmour, Dead Confederate, The Beatles] that you like.",
"style2" : "Your friend [ladika] also likes artist The Doors.",
"style3": "People who like [Pink Floyd, The Beatles] also like The Doors and you like [Pink Floyd, The Beatles].",
"style4": "According to lastfm artist The Doors is similar to artists [Pink Floyd, The Beatles] that you like.",
"style5":"Artist The Doors has the tags [psychedelic, alternative_rock, 60s, 70s, oldies] that you like.",
"style6":"Lastfm users [andrakinoy, swanaldo, milenagallagher, serifethecat, emarongo] with whom you share similar music tastes, also like the artist The Doors.",
"style7":"Artist The Doors is very popular in the lastfm database with number of listeners = 3099831 and playcounts = 128504799",
"rating":"0.5","numOfStyles":"5"}
*/						 
String createJsonRanking(String type, int item_counter, String item, String predicted_rating, 
	String number_of_groundings, HashSet<String> groundings, String url_image){
	
	String output;
	
	output = "{\"type\":\"i" + item_counter.toString() + "-" + type + "\",";	//type
	output += "\"item\":\"" + item + "\",";		 //item
	output += "\"lastfm-url\":\"" + url_image.split("\t")[0] + "\",";		 //url
	output += "\"lastfm-image\":\"" + url_image.split("\t")[1] + "\",";		 //image
	output += "\"data\":\"We recommend to you the artist <bold>" + item + "</bold> because:\","	//data
	
	int counter_style=1;
	//styles
	for(String grounding : groundings){
		output += "\"style" + counter_style + "\":\"" + grounding.replace("\n", "") + "\","
		counter_style++;	
	}
	
	output += "\"rating\":\"" + predicted_rating + "\",";		 //rating
	output += "\"numOfStyles\":\"" + number_of_groundings + "\"}"//num of styles
	
	return output;
	
}

//this structure selects the items we will show to the user for questions Q1-Q3	
void selectItemsForQuestions(TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings, 
	Integer NUM_OF_ITEMS_PER_QUESTION, Integer NUM_OF_ITEMS_TO_RATE,
	HashMap<String, HashSet<String>> q1Items, HashMap<String, HashSet<String>> q2Items, 
	HashMap<String, HashSet<String>> q3Items, HashMap<String, HashSet<String>> q4Items,
	HashMap<String, HashSet<String>> qVennItems){
	
	//select the items for Q3 -- ranking
	//println "About to selectItemsForQ3"
	//for the ranking question Q3 we need to have all seven styles (if possible given the user data)
	selectItemsForQ3(rating_item_groundings, NUM_OF_ITEMS_PER_QUESTION, q3Items)
	/*
	println "---------Q3---------"
	for(String item : q3Items.keySet()){
		println "Item = " + item 
		println "Groundings : "
		println q3Items.get(item);
	}*/
	
	//select the items for Q1 - these items cannot be contained in the structure q3Items -- 3 questions (transparency, confidence, and rate the item)
	//println "About to selectItemsForQ1"
	selectItemsForQ1(rating_item_groundings, NUM_OF_ITEMS_TO_RATE, q1Items, q3Items)
	/*
	println "-------Q1----------"
	for(String item : q1Items.keySet()){
		println "Item = " + item
		println "Groundings : "
		println q1Items.get(item);
	}*/

	//println "About to selectItemsForVenn and the other 3 visuals and the one textual explanation."
	//println "-------VENN----------"
	selectItemsForVenn(rating_item_groundings, NUM_OF_ITEMS_PER_QUESTION, qVennItems, q1Items, q3Items)
	/*
	for(String item : qVennItems.keySet()){
		println "Item = " + item
		println "Groundings : "
		println qVennItems.get(item);
	}*/

	
//	println "About to selectItemsForQ2"
//	//for the visual question Q2 we do not need all the styles so we pick the items with the highest ranking
//	selectItemsForQ2(rating_item_groundings, NUM_OF_ITEMS_PER_QUESTION, q2Items, q1Items, q3Items, qVennItems)
//	println "-------Q2----------"
//	for(String item : q2Items.keySet()){
//		println "Item = " + item
//		println "Groundings : "
//		println q2Items.get(item);
//	}
	
	//for Q4 we call a new function that selects the top k items with the highest value but small number of styles
	//and the top k items with lower value but large number of styles
//	println "About to selectItemsForQ4"
//	println "-------Q4----------"
//	//for the rating Q4 we need to select items and we can re-use items we used above
//	selectItemsForQ4(rating_item_groundings, NUM_OF_ITEMS_TO_RATE, q4Items)
//	for(String item : q4Items.keySet()){
//		println "Item = " + item
//		println "Groundings : "
//		println q4Items.get(item);
//	}

	
}
	
//this function selects the questions for the ranking question (Q3) where we choose the items with the highest number of explanation styles
void selectItemsForQ3(TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings, 
	Integer NUM_OF_ITEMS_PER_QUESTION, HashMap<String, HashSet<String>> q3Items){
	
	//iterate over the structure rating_item_groundings and find the items with the highest number of styles (highest in our case is 7)
	//first call the helper function to find items with 7 styles
	int max=7; //search for 7 explanation styles
	//repeat while the size of the structure q1Items is not full
	while(q3Items.size()<NUM_OF_ITEMS_PER_QUESTION){
	if(max==0){ 	//this means that something bad happened
		println "[ERROR]: In selectItemsForQ3 max = 0 and we did not get enough explanations"
		return;
	}
	Map<Double, Collection<String, HashSet<String>>> map = rating_item_groundings.asMap();
		for(Map.Entry<Double, Collection<String,HashSet<String>>> entry : map.entrySet()){
			Double rating = entry.getKey();
			//for all the pair of persons who happen to have the same rating
			for(HashMap<String, HashSet<String>> item_groundings : entry.getValue()){
				for(String item : item_groundings.keySet()){
					HashSet<String> groundings = item_groundings.get(item);
					//count the number of grounding and if they equal to the max then add this to the q1Items structure only if the size is smaller than NUM_OF_ITEMS_PER_QUESTION
					if(groundings.size()==max){
						q3Items.put(item + "_____" + rating + "_____" + groundings.size(), groundings);
					}
					//check the size of the q1Items structure - if it is full then return 
					if(q3Items.size()==NUM_OF_ITEMS_PER_QUESTION)
						return;
				}
			}
		}
		max--;//reduce the number of explanations styles
	}	
}


//same as the above function with the following difference:
//if the item selected already exists in the structure q3Items then we skip it 
void selectItemsForQ1(TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings,
	Integer NUM_OF_ITEMS_PER_QUESTION, HashMap<String, HashSet<String>> q1Items,
	HashMap<String, HashSet<String>> q3Items){
	
	//iterate over the structure rating_item_groundings and find the items with the highest number of styles (highest in our case is 7)
	//first call the helper function to find items with 7 styles
	int max=7; //search for 7 explanation styles
	//repeat while the size of the structure q1Items is not full
	while(q1Items.size()<NUM_OF_ITEMS_PER_QUESTION){
		if(max==0){ 	//this means that something bad happened
			println "[ERROR]: In selectItemsForQ1 max = 0 and we did not get enough explanations"
			return;
		}
		Map<Double, Collection<String, HashSet<String>>> map = rating_item_groundings.asMap();
		for(Map.Entry<Double, Collection<String,HashSet<String>>> entry : map.entrySet()){
			Double rating = entry.getKey();
			//for all the pair of persons who happen to have the same rating
			for(HashMap<String, HashSet<String>> item_groundings : entry.getValue()){
				for(String item : item_groundings.keySet()){
					HashSet<String> groundings = item_groundings.get(item);
					//count the number of grounding and if they equal to the max then add this to the q1Items structure only if the size is smaller than NUM_OF_ITEMS_PER_QUESTION
					if(groundings.size()==max){
						if(!q3Items.containsKey(item + "_____" + rating + "_____" + groundings.size()))	//insert this only if it is not contained in the structure q3Items
							q1Items.put(item + "_____" + rating + "_____" + groundings.size(), groundings);
					}
					//check the size of the q1Items structure - if it is full then return
					if(q1Items.size()==NUM_OF_ITEMS_PER_QUESTION)
						return;
				}
			}
		}
		max--;//reduce the number of explanations styles
	}
}
	

//for this question we select the items with the highest rating value that are not contained in the lists q1 or q3 or qVenn
//if the item selected already exists in the structure q1Items or q3Items then we skip it
void selectItemsForQ2(TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings,
	Integer NUM_OF_ITEMS_PER_QUESTION, HashMap<String, HashSet<String>> q2Items,
	HashMap<String, HashSet<String>> q1Items, HashMap<String, HashSet<String>> q3Items, 
	HashMap<String, HashSet<String>> qVennItems){
	
	//repeat while the size of the structure q1Items is not full
	while(q2Items.size()<NUM_OF_ITEMS_PER_QUESTION){
		Map<Double, Collection<String, HashSet<String>>> map = rating_item_groundings.asMap();
		for(Map.Entry<Double, Collection<String,HashSet<String>>> entry : map.entrySet()){
			Double rating = entry.getKey();
			//for all the pair of persons who happen to have the same similarity value
			for(HashMap<String, HashSet<String>> item_groundings : entry.getValue()){
				for(String item : item_groundings.keySet()){
					HashSet<String> groundings = item_groundings.get(item);
					if(!q1Items.containsKey(item + "_____" + rating + "_____" + groundings.size()) && !q3Items.containsKey(item + "_____" + rating + "_____" + groundings.size()) && !qVennItems.containsKey(item + "_____" + rating + "_____" + groundings.size()))	//insert this only if it is not contained in the structure q3Items
						q2Items.put(item + "_____" + rating + "_____" + groundings.size(), groundings);
					//check the size of the q1Items structure - if it is full then return
					if(q2Items.size()==NUM_OF_ITEMS_PER_QUESTION)
						return;
				}
			}
		}
	}
}
	
	
//need to select items that have 3 explanation styles: last.fm similarity, item-based-cf similarity, and popularity similarity	
void selectItemsForVenn(TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings,
	Integer NUM_OF_ITEMS_PER_QUESTION, HashMap<String, HashSet<String>> qVennItems,
	HashMap<String, HashSet<String>> q1Items, HashMap<String, HashSet<String>> q3Items){
	
	println "Inside selectItemsForVenn"
	//repeat while the size of the structure qVennItems is not full
	while(qVennItems.size()<NUM_OF_ITEMS_PER_QUESTION){
		Map<Double, Collection<String, HashSet<String>>> map = rating_item_groundings.asMap();
		for(Map.Entry<Double, Collection<String,HashSet<String>>> entry : map.entrySet()){
			Double rating = entry.getKey();
			//for all the pair of persons who happen to have the same similarity value
			for(HashMap<String, HashSet<String>> item_groundings : entry.getValue()){
				for(String item : item_groundings.keySet()){
					HashSet<String> groundings = item_groundings.get(item);
					//call a function that checks if this item contains the 3 groundings needed for the Venn diagrams
					if(containsGroundingsForVenn(groundings)){
						if(!q1Items.containsKey(item + "_____" + rating + "_____" + groundings.size()) && !q3Items.containsKey(item + "_____" + rating + "_____" + groundings.size())){	//insert this only if it is not contained in the structure q3Items
							println "About to insert to Venn the item = " + item + " with size of groundings = " + groundings.size()
							int i=1;
							for(String grounding : groundings){
								println i + "." + grounding;
								i++;
							}
							qVennItems.put(item + "_____" + rating + "_____" + groundings.size(), groundings);
						}
						//check the size of the q1Items structure - if it is full then return
						if(qVennItems.size()==NUM_OF_ITEMS_PER_QUESTION){
							//first prune to make sure we will show only 3 styles and then return 
							println "About to call the pruneStylesForVenn"
							pruneStylesForVenn(qVennItems);
							return;
						}
					}
				}
			}
		}
		println "VENN diagrams... We need to compromise with different styles:"
		//show whichever grounding has at least 3 styles available
		//Map<Double, Collection<String, HashSet<String>>> map = rating_item_groundings.asMap();
		for(Map.Entry<Double, Collection<String,HashSet<String>>> entry : map.entrySet()){
			Double rating = entry.getKey();
			//for all the pair of persons who happen to have the same similarity value
			for(HashMap<String, HashSet<String>> item_groundings : entry.getValue()){
				for(String item : item_groundings.keySet()){
					HashSet<String> groundings = item_groundings.get(item);
					//if the number of groundings is greater or equal to 3 then add it to the structure for Venn diagrams
					if(groundings.size()>=3){
						if(!q1Items.containsKey(item + "_____" + rating + "_____" + groundings.size()) && !q3Items.containsKey(item + "_____" + rating + "_____" + groundings.size())){	//insert this only if it is not contained in the structure q3Items
							qVennItems.put(item + "_____" + rating + "_____" + groundings.size(), groundings);
							println "About to insert to Venn the item = " + item + " with size of groundings = " + groundings.size()
							int i=1;
							for(String grounding : groundings){
								println i + "." + grounding;
								i++;
							}
							
						}
						//check the size of the q1Items structure - if it is full then return
						if(qVennItems.size()==NUM_OF_ITEMS_PER_QUESTION){
							//first prune to make sure we will show only 3 styles and then return
							println "About to call the pruneStylesForVenn"
							pruneStylesForVenn(qVennItems);
							return;
						}
					}
				}
			}
		}
	}
	//here we need to do some compromise and show different styles to the users
	//if(qVennItems.size()<NUM_OF_ITEMS_PER_QUESTION){
		
	//}
}	
	
//given a list of items to show for the Venn diagrams,
//for each item this function prunes the styles in order to have at most 3
void pruneStylesForVenn(HashMap<String, HashSet<String>> qVennItems){
	for(String item : qVennItems.keySet()){
		//println "Item = " + item
		//println "Groundings before pruning:"
		//println qVennItems.get(item);
		
		//if this item contains all the groundings we need for Venn then keep them all and prune the non relevant
		HashSet<String> groundings = qVennItems.get(item);
		int numOfStylesForVenn = numberOfStylesForVenn(groundings);
		println ""
		println "For item " + item + " num of styles for Venn = " + numOfStylesForVenn;
		if(numOfStylesForVenn>=3){//we have all styles we need - just prune the irrelevant ones
			for (Iterator<String> i = groundings.iterator(); i.hasNext();) {
				String grounding = i.next();
				if(!groundingContainsExplanationStyle(grounding)){
					i.remove();
					println "For item " + item + " remove grounding " + grounding;
				}
			}
			//println "--- VENN For the case where all 3 styles are available: Groundings after pruning:"
			//println qVennItems.get(item);
		}
		else{	//in this case first create a list of the styles you want to keep  
				//this is an arrayList so the first 3 elements are the ones we want to keep
				//all the rest we will put them in the end of this list
				//after you are done with the list, then iterate over this list and delete whichever 
				//element is not in there
			println "Alternative for item " + item
			ArrayList<String> groundingsToKeep = new ArrayList();
			for(String grounding : groundings){
				if(groundingContainsExplanationStyle(grounding)){//if this is a style that we are looking for then insert it at the beginning of the list
					groundingsToKeep.add(0,grounding)
				}
				else{	//else insert it at the end of the list 
					groundingsToKeep.add(grounding);
				}
			}
			println "sizeOfList before removing = " + groundingsToKeep.size();
			int sizeOfList = groundingsToKeep.size();
			//groundingsToKeep.subList(3, sizeOfList-1).clear();
			groundingsToKeep.subList(0, 3).clear(); //delete the elements that i need  and keep only the ones I need to prune later from structure qVennItems
			//println "sizeOfList after removing = " + groundingsToKeep.size();
			
			//now iterate over groundingsToKeep - if this element is contained into qVennItems then keep it otherwise delete it
			for(String grounding : groundingsToKeep){	//for each element delete it from the 
				if(!groundings.contains(grounding)){
					println "[ERROR] in pruneStylesForVenn : the element " + grounding + " is not contained in the structure groundings"
				}
				else{
					groundings.remove(grounding);
				}
			}
			println "For item " + item + " num of groundigns after deletion is " + groundings.size();
			//println "--- VENN For the case where NOT all 3 styles are available: Groundings after pruning:"
			println qVennItems.get(item);
		}
	}
}	
	
//helper function for the Venn diagrams
//if the structure contains all the 3 styles needed for Venn it returns true otherwise false	
//the three styles are popularity, item_similarity_lastfm, item_similarity_cf
boolean containsGroundingsForVenn(HashSet<String> groundings){
	int num_of_styles=0;
	for(String grounding : groundings){
		if(groundingContainsExplanationStyle(grounding)){ 			//item-based-CF
			num_of_styles++;
		}
	}
	if(num_of_styles>=3)
		return true;
	else
		return false;
}	

//returns the number of styles we need for Venn are available
Integer numberOfStylesForVenn(HashSet<String> groundings){
	int num_of_styles=0;
	for(String grounding : groundings){
		if(groundingContainsExplanationStyle(grounding)){ 			//item-based-CF
			num_of_styles++;
		}
	}
	return num_of_styles;
}

boolean groundingContainsExplanationStyle(String grounding){
	if(grounding.contains("very popular in the lastfm database") //popularity based
		|| grounding.contains("According to lastfm") 			//last.fm similarity
		|| grounding.contains("People who listen to")){
		return true;
	}
	else{
		return false;
	}
}
	
//this function selects the questions for the ranking question (Q3) where we choose the items with the highest number of explanation styles
void selectItemsForQ4(TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings,
	Integer NUM_OF_ITEMS_TO_RATE, HashMap<String, HashSet<String>> q4Items){
	
	//int num_of_items_per_style = NUM_OF_ITEMS_TO_RATE / 3 + 1; // we divide by 3 since we will have 3 buckets,e.g. one bucket with 7 explanation style, one with 4, and one with 2
	int num_of_items_per_style = 2;
	//iterate over the structure rating_item_groundings and find the items with the highest number of styles (highest in our case is 7)
	//first call the helper function to find items with 7 styles
	int max = 7; 
	
	int counter = 0; //to count how many items we have for each style - at the end of each search it should be
					//equal to num_of_items_per_styles
	
	helperSelectItemsForQ4(rating_item_groundings, num_of_items_per_style, max, q4Items);	//7 styles
	counter=0;
	max--;
	
	helperSelectItemsForQ4(rating_item_groundings, num_of_items_per_style, max, q4Items);	//6 styles
	counter=0;
	max--;
	
	helperSelectItemsForQ4(rating_item_groundings, num_of_items_per_style, max, q4Items);	//5 styles
	counter=0;
	max--;
	
	helperSelectItemsForQ4(rating_item_groundings, num_of_items_per_style, max, q4Items);   //4 styles
	counter=0;
	max--;
	
	helperSelectItemsForQ4(rating_item_groundings, num_of_items_per_style, max, q4Items);   //3 styles
	counter=0;
	max--;
	
	helperSelectItemsForQ4(rating_item_groundings, num_of_items_per_style, max, q4Items);   //2 styles
	counter=0;
	max--;
	
}
	

//this function selects the questions for the ranking question (Q3) where we choose the items with the highest number of explanation styles
void helperSelectItemsForQ4(TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings,
	Integer num_of_items_per_style, int num_of_styles, HashMap<String, HashSet<String>> q4Items){
	
	int i;
	if(num_of_styles==2)//in case the number of styles is the min then we need to increase to find other items
		i=1;
	else	//in the case of middle and max we need to decrease
		i=-1;
		
	int counter = 0;
	while(counter < num_of_items_per_style){
		if(num_of_styles==0){ 	//this means that something bad happened
			println "[ERROR]: In selectItemsForQ4 max = 0 and we did not get enough explanations"
			return;
		}
		Map<Double, Collection<String, HashSet<String>>> map = rating_item_groundings.asMap();
		for(Map.Entry<Double, Collection<String,HashSet<String>>> entry : map.entrySet()){
			Double rating = entry.getKey();
			//for all the pair of persons who happen to have the same rating
			for(HashMap<String, HashSet<String>> item_groundings : entry.getValue()){
				for(String item : item_groundings.keySet()){
					HashSet<String> groundings = item_groundings.get(item);
					//count the number of grounding and if they equal to the max then add this to the q1Items structure only if the size is smaller than NUM_OF_ITEMS_PER_QUESTION
					if(groundings.size()==num_of_styles){
						q4Items.put(item + "_____" + rating + "_____" + groundings.size(), groundings);
						counter++;
						//println "Q4: num_of_styles " + num_of_styles + "\n We recommend to you the item "+ item + " because:\n" + groundings
					}
					//check the size of the q1Items structure - if it is full then return
					if(counter==num_of_items_per_style)
						return;
				}
			}
		}
		num_of_styles = num_of_styles + i;
	}
}
	

	
		
	
void generateRecommendations(GroundRuleStore groundRuleStore, HashMap<String, String> userIDsToNames, 
	HashMap<String, String> itemIDsToNames, HashMap<String, String> tagIDsToNames,
	TreeMultimap<Double, String> rating_item,
	HashMap<String, HashSet<String>> item_grounding, int LIMIT){
	//to print the doubles with 3 decimal digits
	DecimalFormat df3 = new DecimalFormat(".###");
	for(GroundRule k : groundRuleStore.getGroundRules()){
		//println "GroundRule = " + k;
		int num_of_predictions=0;
		String ratingPredicateValue;
		//print "Kernel: weight =  " + df3.format(k.kernel) + " incompatibility = " + df3.format(k.getIncompatibility());
		//print "Kernel: weight =  " + k.kernel + " incompatibility = " + df3.format(k.getIncompatibility());
	
		//System.out.println "Weight = " + k.getWeight().getWeight();
		//System.out.println "Incompatibility = " + k.getIncompatibility();
		//System.out.println "Rule = " + k.getRule()
	
		String ruleBody = k.getRule().toString().replaceAll("\\{.*?\\}", "").replaceAll("RATED\\(.*?\\)", "").replaceAll("USER\\(.*?\\)", "").replaceAll("ITEM\\(.*?\\)", "").replaceAll("\\( \\( \\(", "").replaceAll("&  \\) & ","").replaceAll("\\) \\)","\\)").replaceAll("& USER","USER").replaceAll("( )+", " ").trim();
		//String ruleBody = k.kernel.toString().replaceAll("\\{.*?\\}", "").replaceAll("RATED\\(.*?\\)", "").replaceAll("USER\\(.*?\\)", "").replaceAll("ITEM\\(.*?\\)", "").replaceAll("\\( \\( \\(", "").replaceAll("&  \\) & ","").replaceAll("\\) \\)","\\)").replaceAll("& USER","USER").replaceAll("( )+", " ").trim();
		String newRuleBody = ruleBody.replaceAll("^\\) & ","").replaceAll(">>","->").trim();
		//String weightValue = k.getWeight().toString().split("=")[1];
		String weightValue = k.getWeight().toString().split(":")[0];
		String ruleWeight = ruleBody + "," + weightValue;	//keeps both the body of the rule AND the weight
		//println "\n ruleBody=" + newRuleBody;
		//print " weightValue = " + weightValue + " incompatibility = " + df3.format(k.getIncompatibility()) + " ";
		String grounding = weightValue + "\t" + df3.format(k.getIncompatibility()) + "\t";
	
		//println "\nruleWeightValue = " + ruleWeight
		int ruleID;
	
	
		//what we need to do now is for each predicate of the rule to print the properties field,
		//i.e., the instantiations for this predicate
		//we know that the first time we see the predicate rating this is the observed value otherwise
		//this is the predicted value
		
		String ruleProperties = "";
		//find how many time the string RATING appears in the rule
		//it it is just one then we have one rating only which is the predicted value
		//if it is 2 then we have two ratings: one is the observed and the other
		//is the predicted
		int occurences = findOccurencesOfRATING(newRuleBody);
		//here we split the rule to each predicates
		String[] ruleTokens = newRuleBody.split("\\W")
		Set<Atom> atoms = k.atoms //get the atoms
		boolean firstRating=true
		
		for(String token : ruleTokens)
		//for each token iterate over the atoms and find the atom that has the same value with this
		//e.g. if token=avg_user_rate then find the atom which has as substring the avg_user_rate
			if(token.size()>3){ //any token with size >3 has stored the predicate and not some kind of symbols like U,I,...
				for(Atom atom : atoms){//iterate over all atoms to find the one that is same with the current predicate
					if(atom.toString().contains(token)){
						if(occurences==2 && token.equals("RATING")){ //check if this is the first or second time we see the rating
							//println "atom " + atom
							if(firstRating){ //in this case we need to find the observation
								//i think I should replace the following line with if(atom instanceof GroundAtom){
								if(!(atom instanceof RandomVariableAtom)){ //if it is not randomVariableAtom it means that it is observedAtom
									String predicateProperties = GenerateProperties(atom.toString().replaceAll("\\s",""), userIDsToNames, itemIDsToNames, tagIDsToNames)
									//println "adding Obs " + predicateProperties
									if(ruleProperties.isEmpty())
										 ruleProperties = predicateProperties;
									else
										 ruleProperties = ruleProperties + "," + predicateProperties;
									//println "updated: " + ruleProperties
									firstRating=false;
									break;
								}
							}
							else{			//in this case we need to find the prediction
								if(atom instanceof RandomVariableAtom){
									String predicateProperties = GenerateProperties(atom.toString().replaceAll("\\s",""), userIDsToNames, itemIDsToNames, tagIDsToNames)
									if(ruleProperties.isEmpty())
										 ruleProperties = predicateProperties;
									else
										 ruleProperties = ruleProperties + "," + predicateProperties;
									//println "updated: " + ruleProperties
									//ratingValue = df3.format(atom.getValue()).toString()
									//println "adding Pred " + predicateProperties;// + " value = " + ratingValue;
									break;
								}
							}
					 
						}
						else{
							String predicateProperties = GenerateProperties(atom.toString().replaceAll("\\s",""), userIDsToNames, itemIDsToNames, tagIDsToNames)
							//println "adding " + predicateProperties
							if(ruleProperties.isEmpty())
								 ruleProperties = predicateProperties;
							else
								 ruleProperties = ruleProperties + "," + predicateProperties;
							//println "updated: " + ruleProperties
							break;
						}
					}
				}
			}
	
		//Set<Atom> atoms = k.atoms
		for(Atom atom : atoms){
			String value;
			String type;
			//here translate the values of the atoms to real values
			//eg translate the users_are_firends(123,456) to users_are_friends(mike,tina)
			//do not print the atoms RATED since they are not informative for the user perspective
			if(!atom.toString().contains("RATED") && !atom.toString().contains("USER(") && !atom.toString().contains("ITEM(")){
				//println "print atom=" + atom
				String generatedAtom = GenerateGroundedAtom(atom.toString().replaceAll("\\s",""), userIDsToNames, itemIDsToNames, tagIDsToNames)
		
				//in all the cases above insert the info into the relations file

				//for this particular predicate we also need to add the relations between the predicate
				//and the users and items
				//and the relationships between the rule and the predicates
				//GenerateRelations(atom.toString().replaceAll("\\s",""), userIDsToNames, itemIDsToNames, firstItemID, secItemID, writeToRelFilePredicateID.toString(), currentRuleID.toString(), relationsFile);

				if (atom instanceof RandomVariableAtom){
					value = df3.format(atom.getValue()).toString()
					ratingPredicateValue = generatedAtom + "," + df3.format(atom.getValue()).toString()
				}
			
				if (atom instanceof RandomVariableAtom){
					num_of_predictions++;
					value = df3.format(atom.getValue()).toString()
					type = "Pred"
					//print "\t" + type + ": " + generatedAtom + " Value: " + value
					grounding += type + ":" + generatedAtom + " Value:" + value + "\t";
				}
				else if(atom instanceof GroundAtom){
					if(atom.toString().contains("RATING")){
						value = (atom.getValue()).toString();
					}
					else{
						value = atom.getValue().toString();
					}
					type = "Obs"
					//print "\t" + type + ": " + generatedAtom + " Value: " + value;
					grounding += type + ":" + generatedAtom + " Value:" + value + "\t";
				}
				//System.out.println(String.format("\tObs:%s Value:%.2f", generatedAtom, atom.getValue().toDouble()));
				else
					println "Error - Unknown type of atom"
			
			
			}
			//currentPredicateID++; //update the uniquID for the predicate
		
		}
		grounding += num_of_predictions
		//call this function that given the current grounding, it updates the structures users_sortedRatings_items and users_recs_groundings
		updateRecSysStructures(grounding, rating_item, item_grounding, LIMIT);
	}

}

String crawlData(String CMD, String HOME_DIR){
		//println "---------About to call the crawler...----------"
		try {
			//Process p = Runtime.getRuntime().exec("./last.fm-mturk/one_user.sh 1user");
			Process p = Runtime.getRuntime().exec(CMD, null, new File(HOME_DIR)); p.waitFor();
	
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line=reader.readLine();
		
			String user_md5;
			while (line != null) {
				System.out.println(line);
				//here implement the code that retrieves the md5 for this user
				if (line.startsWith("#[INF] Hash is ")) {
					String[] tokens = line.split(" ");
				  if (tokens.length > 0) { user_md5 = tokens[tokens.length-1]; } // last one
				  else{ println "Could not receive md5 for the user. About to exit..." ; return; }
				  //println "Retrieved the md5 user : " + user_md5;
				}
				line = reader.readLine();
			}
			return user_md5;
		}
		catch(IOException e1) {
			println "IOException when calling the crawler."
			org.codehaus.groovy.runtime.StackTraceUtils.sanitize(new Exception(e1)).printStackTrace();
			return;
		}
		catch(InterruptedException e2) {
			println "InterruptedException when calling the crawler."
			org.codehaus.groovy.runtime.StackTraceUtils.sanitize(new Exception(e2)).printStackTrace();
			return;
		}
		
		//System.out.println("--------------Crawler is done.-------------");
}
	
void deleteDataBase(String CMD, String user_md5){
	//println "About to delete the database..."
	try {
		CMD = "dropdb " + user_md5 ;
		//println "CMD "+CMD
		Process p = Runtime.getRuntime().exec(CMD);
		p.waitFor();

		BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line=reader.readLine();

		while (line != null) {
			System.out.println(line);
			line = reader.readLine();
		}
	}
	catch(IOException e1) {
		println "IOException when deleting the database."
		org.codehaus.groovy.runtime.StackTraceUtils.sanitize(new Exception(e1)).printStackTrace();
		return;
	}
	catch(InterruptedException e2) {
		println "InterruptedException while deleting the database."
		org.codehaus.groovy.runtime.StackTraceUtils.sanitize(new Exception(e2)).printStackTrace();
		return;
	}
	//println "Deleted the database!"
}
	
	
PSLModel createModel(Object object, DataStore data){
	
	
	PSLModel m = new PSLModel(this, data)
	
	//DEFINITION OF THE MODEL
	//general predicates
	m.add predicate: "rating",			types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	m.add predicate: "rated",			types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID] //this is used in the blocking mechanism
	m.add predicate: "ratedTrain",			types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID] //this is used to make sure that inference will not be collective

	//item similarities
	//m.add predicate: "sim_pearson_items",	types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	//m.add predicate: "sim_cosine_items",    types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	//m.add predicate: "sim_adjcos_items",	types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	m.add predicate: "sim_jaccard_items",   types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	m.add predicate: "sim_lastfm_items",   types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	m.add predicate: "sim_tags_items",   types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	
	
	//user similarities
	//m.add predicate: "sim_pearson_users",   types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	m.add predicate: "sim_cosine_users",    types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	m.add predicate: "sim_jaccard_users",   types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	
	//social info
	//this predicate takes values in the interval [0,1] and takes into account the friend similarity (it is NOT binary)
	m.add predicate: "users_are_friends",	types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	
	//content info
	m.add predicate: "item_has_tag",  types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]
	
	m.add predicate: "item_is_popular", types: [ConstantType.UniqueIntID] //popular artists in last.fm
	
	//RULES
	//SIMILARITIES
	
	//ITEMS
	//sim jacacrd (ratings)
	m.add rule :  (ratedTrain(U,I1) & rated(U,I1) & rated(U,I2) & rating(U,I1) & sim_jaccard_items(I1,I2)) >> rating(U,I2), weight: 1, squared:true;
	
	//sim last.fm
	m.add rule :  (ratedTrain(U,I1) & rated(U,I1) & rated(U,I2) & rating(U,I1) & sim_lastfm_items(I1,I2)) >> rating(U,I2), weight: 50, squared:true;
	
	//sim jaccard (tags)
	m.add rule :  (ratedTrain(U,I1) & rated(U,I1) & rated(U,I2) & rating(U,I1) & sim_tags_items(I1,I2)) >> rating(U,I2), weight: 1, squared:true;
	
	//USERS
	//cosine users
	m.add rule :  (rated(U1,I) & rated(U2,I) & rating(U1,I) & sim_cosine_users(U1,U2)) >> rating(U2,I), weight: 1, squared:true;
	
	//jaccard users
	m.add rule :  (rated(U1,I) & rated(U2,I) & rating(U1,I) & sim_jaccard_users(U1,U2)) >> rating(U2,I), weight: 1, squared:true;
	
	//SOCIAL RULES
	//friendships
	m.add rule : (rated(U2,I) & rated(U1,I) & users_are_friends(U1,U2) & rating(U1,I)) >> rating(U2,I), weight: 1, squared:true;
	
	
	//CONTENT
	//tags
	m.add rule : (ratedTrain(U,I1) & rated(U,I1) & rated(U,I2) & rating(U,I1) & item_has_tag(I1,T) & item_has_tag(I2,T)) >> rating(U,I2), weight: 0.5, squared:true;
	
	//if the item in among the top 1,000 in the last.fm database then any user might like it
	m.add rule : (rated(U,I) & item_is_popular(I)) >> rating(U,I), weight: 2, squared: true;
	
	m.add rule : ~rating(U,I), weight: 100, squared: true;

	return m;
	
}

String loadData(Partition evidencePartition, Partition targetPartition, DataStore data, String userDir,
	int MINSIZEOFFRIENDS, int MINSIZEOFARTISTS){
		
		//println "Time before reading the files " + new Date();
	
	    //item similarities 
	
	    //simialrities: item-item jaccard
	    //println "About to load the item-item jaccard similarities"
	    Inserter inserter3 = data.getInserter(sim_jaccard_items, evidencePartition)
	    inserter3.loadDelimitedDataTruth(userDir + "artist-artist.user.jaccard.top20.ones");

	    //similarities: item-item last.fm
	    //println "About to load the item-item last.fm similarities"
	    Inserter inserter4 = data.getInserter(sim_lastfm_items, evidencePartition)	
	    inserter4.loadDelimitedDataTruth(userDir + "artist-artist.lastfm.top20.ones");

	    //similarities: item-item tag 
	    //println "About to load the item-item tag similarities"
	    Inserter inserter5 = data.getInserter(sim_tags_items, evidencePartition)
	    inserter5.loadDelimitedDataTruth(userDir + "artist-artist.tags.jaccard.top20.ones");

	    //user similarities
	
	    //similarities: user-user cosine
	    //println "About to load the user-user artist cosine similarities"
	    Inserter inserter7 = data.getInserter(sim_cosine_users, evidencePartition)
	    inserter7.loadDelimitedDataTruth(userDir + "inputuser-user.artist.cosine.top20.ones");

	    //similarities: user-user jaccard
	    //println "About to load the user-user artist jaccard similarities"
	    Inserter inserter1 = data.getInserter(sim_jaccard_users, evidencePartition)
	    inserter1.loadDelimitedDataTruth(userDir + "inputuser-user.artist.jaccard.top20.ones");
	
	    //similarities: friend-friend cosine
	    //println "About to load the friend-friend cosine similarities"
	    Inserter inserter8 = data.getInserter(users_are_friends, evidencePartition)
	    //inserter8.loadDelimitedDataTruth(userDir + "inputfriends-user-user.artist.cosine.binary");
	    inserter8.loadDelimitedData(userDir + "inputuser_friend.graph");
		//here test the size of the structure if it is less than 10 we need to send error message.
		
		int sizeOfFriends = checkSizeOfFile(userDir + "inputuser_friend.graph");
		if(sizeOfFriends < MINSIZEOFFRIENDS){
			println "[ERROR]: You need to follow at least " + MINSIZEOFFRIENDS + " users to participate to the study. Current size of friends = " +  sizeOfFriends;
			println "[ERROR]: About to exit now..."
			return "[ERROR]: You need to follow at least " + MINSIZEOFFRIENDS + " users to participate to the study."
		}

	    //tags
	    //println "About to load the artist has tag"
	    Inserter inserter10 = data.getInserter(item_has_tag, evidencePartition)
	    inserter10.loadDelimitedData(userDir + "artist-tags");
	
	    //top artists in last.fm database
	    //println "About to load the top 1,000 artists in the last.fm database"
	    Inserter inserter11 = data.getInserter(item_is_popular, evidencePartition)
	    inserter11.loadDelimitedData(userDir + "top_1000_artists.id.only");
	
	
	    //perform inference
	    //we put in the same partition things that are observed
	    //println "About to load the rated file"
	    Inserter inserter12 = data.getInserter(rated, evidencePartition)
	    inserter12.loadDelimitedData(userDir + "rated"); //the rated file is created as cut -f1,2 inputuser_artist.topredict user_artist.graph
	
	    //println "About to load the existing ratings of the system"
	    Inserter inserter13 = data.getInserter(ratedTrain, evidencePartition);
	    inserter13.loadDelimitedData(userDir + "rated.train");
		//here check the size of the file - if it contains less than 10 artists 
		//then we need to send a mesage to the user that he cannot participate in the study
		int sizeOfArtists = checkSizeOfFile(userDir + "rated.train");
		if(sizeOfArtists==0)
			return "[ERROR]: User does not exist in last.fm "
		if(sizeOfArtists < MINSIZEOFARTISTS){
			println "[ERROR]: You need to have at least " + MINSIZEOFFRIENDS + " in your profile to participate to the study. Current size of artists " + sizeOfArtists
			println "[ERROR]: About to exit now..."
			return "[ERROR]: You need to have at least " + MINSIZEOFARTISTS + " in your profile to participate to the study."
		}
		
	    //println "About to load the existing ratings of the system"
	    Inserter inserter14 = data.getInserter(rating, evidencePartition);
	    inserter14.loadDelimitedDataTruth(userDir + "user_artist.graph.binary");
	
	    // to predict
	    //println "About to load the ratings I want to predict"
	    Inserter inserter16 = data.getInserter(rating, targetPartition)
	    inserter16.loadDelimitedData(userDir + "inputuser_artist.topredict");

	    //println "Done with reading the data for the inference - Time " + new Date();
		
		return "";
}
	
	int checkSizeOfFile(String fileName){
		
		//println "About to check the size of the file " + fileName
		int sizeOfStructure=0;
		def ids_names = new File(fileName)
		ids_names.eachLine {
			sizeOfStructure++;
		}
		return sizeOfStructure;
	}
	
    //each grounding is of the form weight\tincompatibility\tgrounging\number_of_predictions
    void updateRecSysStructures(String grounding, TreeMultimap<Double, String> rating_item,
    	HashMap<String, HashSet<String>> item_grounding, int LIMIT){
    	
    	//first given the grounding we need to find the user that this grounding refers to
    	//we can find this from the predicted rating predicate
    	String user = getUserFromGrounding(grounding)
    	String item = getItemFromGrounding(grounding)
    	Double rating = getValueFromGrounding(grounding)
    	//String user_item = user + "\t" + item
    	
    	//println user + "\t" + item + "\t" + rating
    	//if the size of the structure is 0 this means that this is the first grounding for this actual user
    	if(rating_item.empty){
    		//println "For the first time - found user " + user 
    		//first we need to create an instance of the treeMulimap structure
    		//TreeMultimap<Double, String> rating_item = new TreeMultimap<>(DECREASING_DOUBLE_COMPARATOR, Ordering.natural());
    		//then insert into it the value and the grounding
    		rating_item.put(rating, item)
    		//then insert the <user,multimap> into the structure users_sortedRatings_items
    		//users_sortedRatings_items.put(user,rating_item)
    		
    		//now update the structure users_recs_groundings
    		if(item_grounding.containsKey(item))
    			println "[ERROR]: Something went wrong in updateRecSysStructures"
    		else{
    			HashSet<String> groundings = new HashSet();
    			groundings.add(grounding)
    			item_grounding.put(item, groundings)
    		}
    	}
    	else{	//this means that the current user exists in the structure users_sortedRatings_items
    		//println "I have seen in the past the user " + user
    		if(!item_grounding.containsKey(item)){//first check if this user+item combination exists in the structure  users_recs_groundings
    			//println "user+item = " + user_item + " does not exist in the structure users_items_groundings"
    			//if it does not exist this means that we need to check if we need to insert this item in the structures users_recs_groundings and users_sortedRatings_items
    			//we will only insert this entry if the predicted value is one of the top 20 for this user
    			//first get access to the TreeMultimap for this user
    			//TreeMultimap<Double, String> ratings_items = users_sortedRatings_items.get(user)
    			if(rating_item.size()<=LIMIT){//if the size of this structure is smaller than 20 then we can insert this current entry
    				//println "Size of the treemultimap for user " + user + " is less than 20"
    				rating_item.put(rating, item)	//update the treeMap
    				//also insert the user+item in the structure users_items_groundings
    				HashSet<String> firstGrounding = new HashSet();
    				firstGrounding.add(grounding)		
    				item_grounding.put(item, firstGrounding)
    			}
    			else{	//in case the size is larger than 20 then we need to check the value of the last element of the treemap
    					//since the treemap is sorted then the last element will have the lowest value
    				//SortedSet<Double> last_values = values_items.get(values_items.keySet().last())	//I guess that these values should all be the same 
    				//println "The size of the treemultimap is bigger than 20"
    				Double lowest_rating = rating_item.asMap().lastKey();
    				//println "last value = " + lowest_rating
    				if(lowest_rating<rating){ //only in this case remove the old entry and insert the new one, otherwise do nothing
    					
    					//println "Remove the old items and insert new one"
    					//first get acces to all the items that have this low rating value
    					Set<String> lowest_items = rating_item.get(lowest_rating)
    					//println "The list of lowest items: " + lowest_items;
    					rating_item.removeAll(lowest_rating)	//remove this entry from the structure users_sortedRatings_items
    					rating_item.put(rating, item)		//and insert the new one
    					
    					//for each combination user + item with low value, remove this from the structure
    					for(String lowest_item : lowest_items){
    						String lowest_user_item = user + "\t" + lowest_item;
    						item_grounding.remove(lowest_user_item) //first remove the entry with the low rating from the structure users_recs_groundings
    					}
    					
    					//then create a new groundings set and add the new grounding
    					HashSet<String> firstGrounding = new HashSet();
    					firstGrounding.add(grounding)
    					item_grounding.put(item, firstGrounding)
    					
    				}
    			}
    			
    			
    		}
    		else{	//in case that the combination exists in the structure 
    				//then the only thing we need to do is to insert this specific grounding to the structure
    			HashSet<String> groundings = item_grounding.get(item)
    			//add to the groundings set the new grounding
    			groundings.add(grounding)
    			
    		}
    	}
    	
    }
    	
    String getUserFromGrounding(String grounding){
    	return grounding.split("Pred:RATING")[1].split(",")[0].replace("(", "");
    }
    
    String getItemFromGrounding(String grounding){
    	return grounding.split("Pred:RATING")[1].split(",")[1].split("\\)")[0];
    }
    
    Double getValueFromGrounding(String grounding){
    	return Double.parseDouble(grounding.split("Pred:RATING")[1].split("Value:")[1].split("\t")[0]);
    	//return grounding.split("Pred:RATING")[1].split(",")[1].split("\\)")[0];
    }
    
    Double getWeightFromGrounding(String grounding){
    	return Double.parseDouble(grounding.split("\t")[0])
    }
    
    Double getIncompatibilityFromGrounding(String grounding){
    	return Double.parseDouble(grounding.split("\t")[1])
    }
    
    
    void printRecommendations(TreeMultimap<Double, String> sortedRatings_items, 
    							HashMap<String, HashSet<String>> items_groundings,
								HashMap<String,String> cleanArtistNameToRealArtistName, String HOME_DIR){
    
    	//iterate over the structure users_sortedRatings_items to print for each user his/her top items with the ratings 
    	//Set<String> users = users_sortedRatings_items.keySet();
    	//for(String user : users){
    		//TreeMultimap<Double, String> ratings_items = sortedRatings_items.get(user)
    		println "\nRecommend items: "
    		int i=1;
    		Set<Double> ratings = sortedRatings_items.keySet();
    		for(Double rating : ratings){
    			Set<String> items = sortedRatings_items.get(rating);
    			for(String item : items){
    				print i + "." + item + " value=" + rating //+ " with the groundings "
    				
					String url_image = generateURLAndImage(item, cleanArtistNameToRealArtistName, HOME_DIR)
					//println " url_image= " + url_image;
					
    				//the below prints the groundings as well
    				/*
    				String user_item = user + "\t" + item;
    				if(users_items_groundings.containsKey(user_item)){
    					HashSet<String> groundings = users_items_groundings.get(user_item)
    					for(String grounding : groundings)
    						println grounding
    				}
    				else{
    					println "Something went wrong in printRecommendations while accessing the users_items_groundings"
    				}
    				*/
    				i++; 
    			}
    		}
    		
    		
    	//}
    	
    	
    }
																
    void generateExplanationsForUser(String userID, String user_md5, TreeMultimap<Double, String> ratings_items,
    	HashMap<String, HashSet<String>> items_groundings, HashMap<String, String> artists_stats,
    	int THRESHOLD, /*String outDir,*/
		TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings,
		HashMap<String,String> item_textual,
		HashMap<String,String> item_jsonCollapsible,
		HashMap<String,String> item_jsonVenn,
		HashMap<String,String> item_jsonCluster, 
		HashMap<String,String> item_jsonRadial,
		HashSet<String> offensiveTags,
		HashMap<String, Double> user_user_sim,
		HashMap<String, Double> artist_artist_lastfmsim,
		HashMap<String,Double> artist_artist_tagsim,
		HashMap<String,Double> artist_artist_CFsim,
		HashMap<String, Double> friend_friend_sim,
		HashMap<String, Double> tag_popularity,
		HashMap<String, String> namesToUserIDs,
		HashMap<String, String> namesToItemIDs,
		HashMap<String, String> namesToTagIDs){
    	
    	//println "About to generate Explanations for the current user..."
    	//Set<String> users = sortedRatings_items.keySet();
    	//for(String user : users){
    		//TreeMultimap<Double, String> ratings_items = sortedRatings_items.get(user)
    		
    		//this code is for the file creation 
    		
    		//these are the template files which are the same for all users - we will just need to copy those
//    		String sourceClusterDendrogramIndex = outDir + "ClusterDendrogram" + java.io.File.separator + "index.html";
//    		String sourceCollapsibleTreeIndex = outDir + "CollapsibleTree" + java.io.File.separator + "index.html";
//    		String sourceCollapsibleTreeJs = outDir + "CollapsibleTree" + java.io.File.separator + "dndTree.js";
//    		String sourceRadialDendrogramIndex = outDir + "RadialDendrogram" + java.io.File.separator + "index.html";
    		
    		//create the directory for this user
//    		def userDir = outDir + java.io.File.separator + user_md5
//    		new File(userDir).mkdir();	//in this directory we will store the output data for this user
//    		//create the file where we will store the textual explanations
//    		def textExplFile = createFile(userDir + java.io.File.separator + "expl.text");
    		//make 3 different directories - one for each visual
    		
    		//create the directory for ClusterDendrogram and copy the index.html file
//    		new File(userDir + java.io.File.separator + "ClusterDendrogram").mkdir();
//    		//copy the template files
//    		String targetClusterDendrogram = userDir + java.io.File.separator + "ClusterDendrogram" + java.io.File.separator + "index.html"
//    		File clusterDendIndex = new File(targetClusterDendrogram);
//    		if(!clusterDendIndex.exists()) //create it only if it does not exist
//    			Files.copy(Paths.get(sourceClusterDendrogramIndex), Paths.get(targetClusterDendrogram));
    		
    		//create the flare.csv file
    		
    		//create the directory for CollapsibleTree and copy the index.html and dnd.Tree.js files
//    		new File(userDir + java.io.File.separator + "CollapsibleTree").mkdir();
//    		//copy the template files
//    		String targetCollapsibleTreeIndex = userDir + java.io.File.separator + "CollapsibleTree" + java.io.File.separator + "index.html"
//    		File collapsideTreeIndex = new File(targetCollapsibleTreeIndex);
//    		if(!collapsideTreeIndex.exists())
//    			Files.copy(Paths.get(sourceCollapsibleTreeIndex), Paths.get(targetCollapsibleTreeIndex));
    		
//    		String targetCollapsibleTreeJs = userDir + java.io.File.separator + "CollapsibleTree" + java.io.File.separator + "dndTree.js"
//    		File collapsideTreeJs = new File(targetCollapsibleTreeJs);
//    		if(!collapsideTreeJs.exists())
//    			Files.copy(Paths.get(sourceCollapsibleTreeJs), Paths.get(targetCollapsibleTreeJs));
    		
    		//create the directory for RadialDendrogram and copy the index.html files
//    		new File(userDir + java.io.File.separator + "RadialDendrogram").mkdir();
//    		//copy the template files
//    		String targetRadialDendrogram = userDir + java.io.File.separator + "RadialDendrogram" + java.io.File.separator + "index.html"
//    		File radialDendrogramIndex = new File(targetRadialDendrogram);
//    		if(!radialDendrogramIndex.exists())
//    			Files.copy(Paths.get(sourceRadialDendrogramIndex), Paths.get(targetRadialDendrogram));
    		
    		//println "\nFor user " + user_md5 + " recommend items: "
    		Set<Double> ratings = ratings_items.keySet();
    		int indexCount = 1;
    		for(Double rating : ratings){
    			Set<String> items = ratings_items.get(rating);
    			for(String item : items){	//now I have all the information that I need: user, item, value and groundings
    				
    				//for each item, create an flare.json file for the Collapsible Tree
    				//String flareJson = userDir + java.io.File.separator + "CollapsibleTree" + java.io.File.separator + "flare" + indexCount + ".json";
    				//File flareJsonFile = createFile(flareJson);
    				StringBuilder jsonContentCollapsible = new StringBuilder();
    				
					StringBuilder jsonContentVenn = new StringBuilder();
					
					//for each item, create an flare.csv file which will be the same for Cluster Dendrogram and Radial Dendrogram
    				//String flareClusterCSV = userDir + java.io.File.separator + "ClusterDendrogram" + java.io.File.separator + "flare" + indexCount + ".csv";
    				//File flareClusterCSVFile = createFile(flareClusterCSV);
    				
    				//String flareRadialCSV = userDir + java.io.File.separator + "RadialDendrogram" + java.io.File.separator + "flare" + indexCount + ".csv";
    				//File flareRadialCSVFile = createFile(flareRadialCSV);
    				
    				StringBuilder csvContent = new StringBuilder(); //for flareClusterCSVFile
					StringBuilder csvContentRadial = new StringBuilder(); //for flareRadialCSVFile 
					
    				indexCount++;
    				//the below prints the groundings as well
    				//println "\n" + item + " because: "
    				//write this phrase to the beginning of the sentence
    				//textExplFile.append("\nWe recommend to you the artist " + item + " because:\n");
    				String user_item = user_md5 + "\t" + item;
    				if(items_groundings.containsKey(item)){
    					HashSet<String> groundings = items_groundings.get(item)
    					//the call to this function updates the content for the structures jsonContent and csvContent
						generateStructuresForExplanationsForOneItem(userID, user_md5, item, rating, groundings, artists_stats, 
							THRESHOLD, /*textExplFile,*/ jsonContentCollapsible, jsonContentVenn, csvContent, csvContentRadial, 
							rating_item_groundings, item_textual, item_jsonCollapsible, item_jsonVenn, item_jsonCluster, 
							item_jsonRadial, offensiveTags, user_user_sim, artist_artist_lastfmsim, 
							artist_artist_tagsim, artist_artist_CFsim, friend_friend_sim, tag_popularity,
							namesToUserIDs, namesToItemIDs, namesToTagIDs)
    					//println "Final call: json  = " + jsonContent.toString()
						//println "Final call: csv = " + csvContent.toString()
						//flareJsonFile.append(jsonContent.toString())
						//flareClusterCSVFile.append(csvContent.toString())
						//flareRadialCSVFile.append(csvContentRadial.toString())
						
    				}
    				else{
    					println "Something went wrong in printRecommendations while accessing the users_items_groundings"
    				}
    			}
    		}
    	//}
    }
    	
    //generate the structures we need to generate with the similar items, users, content items, and friends. 
    void generateStructuresForExplanationsForOneItem(String userID, String user, String item, Double rating, 
    	HashSet<String> groundings, HashMap<String, String> artists_stats, int THRESHOLD,
    	/*def textExplFile,*/
    	StringBuilder jsonContentCollapsible, StringBuilder jsonContentVenn, 
		StringBuilder csvContent, StringBuilder csvContentRadial,
		TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings,
		HashMap<String,String> item_textual,
		HashMap<String,String> item_jsonCollapsible, 
		HashMap<String,String> item_jsonVenn,
		HashMap<String,String> item_jsonCluster, 
		HashMap<String,String> item_jsonRadial,
		HashSet<String> offensiveTags,
		HashMap<String, Double> user_user_sim,
		HashMap<String, Double> artist_artist_lastfmsim,
		HashMap<String,Double> artist_artist_tagsim,
		HashMap<String,Double> artist_artist_CFsim,
		HashMap<String, Double> friend_friend_sim,
		HashMap<String, Double> tag_popularity,
		HashMap<String, String> namesToUserIDs,
		HashMap<String, String> namesToItemIDs,
		HashMap<String, String> namesToTagIDs){
    	
    	//for now we have 5 different styles: content, social, item-based, user-based, tags
    	//for the avg item rating we do not need to store this since the explanation will be the same
    	HashMap<String, String> sim_items_content = new HashMap();	//String is of the form <item,weight\tincompatibility\tsimilarity\tnumber_of_predictions>
    	HashMap<String, String> sim_friends = new HashMap();		//String is of the form <friend,weight\tincompatibility\tsimilarity\tnumber_of_predictions>
    	HashMap<String, String> sim_items_CF = new HashMap();		//String is of the form <item,weight\tincompatibility\tsimilarity\tnumber_of_predictions>
    	HashMap<String, String> sim_items_lastfm = new HashMap();		//String is of the form <item,weight\tincompatibility\tsimilarity\tnumber_of_predictions>
    	HashMap<String, String> sim_users_CF = new HashMap();		//String is of the form <user,weight\tincompatibility\tsimilarity\tnumber_of_predictions>
    	HashMap<String, String> tags = new HashMap();				//String is of the form <tag,weight\tincompatibility\tsimilarity\tnumber_of_predictions>
    	String popular_item;										//for this type I only need the number of listeners and playcounts of listened songs. The string contains: num_listeners\tplaycount\tweight\tincompatibility
    	
    	for(String grounding : groundings){	//for each grounding find in which category it belongs to
    		//println grounding
    		//the weight, incompatibility, and number_of_predictions are the same for all rules so we can compute these 2  values here
    		Double weight = getWeightFromGrounding(grounding)
    		Double incompatibility = getIncompatibilityFromGrounding(grounding)
    		//the number of predictions is the last element of the grounding when spliting by \t
    		String[] elements = grounding.split("\t")
    		//println "test = " + elements[elements.size()-1]
    		//Integer num_of_predictions = 1
    		int temp = elements.size()-1;
    		Integer num_of_predictions; //= Integer.valueOf(elements[elements.size()-1])	//access the last element of the structure
    		//Integer num_of_predictions = Integer.valueOf("1")	//access the last element of the structure
    		
    		//println "weight = " + weight + " incompatibility = " + incompatibility
    		
    		if(grounding.contains("ITEM_IS_POPULAR")){
				num_of_predictions= 1;
    			String pop_item = grounding.split("ITEM_IS_POPULAR\\(")[1].split("\\)")[0]
				//println "---popular item--- " + pop_item
				if(artists_stats.containsKey(pop_item)){//in case something goes wrong
    				String listeners = artists_stats.get(pop_item).split("\t")[0];
    				String playcount = artists_stats.get(pop_item).split("\t")[1];
    				popular_item = listeners + "\t" + playcount + "\t" + weight + "\t" + incompatibility + "\t" + num_of_predictions;
    			//	println "---popular item--- " + popular_item;
				}
    		}
    		else{
    			num_of_predictions = Integer.valueOf(elements[elements.size()-1])
    			//if(grounding.contains("SIM_COSINE_ITEMS") || grounding.contains("SIM_ADJCOS_ITEMS") || grounding.contains("SIM_PEARSON_ITEMS")){	//this means that this is an item-based style explanation
    			//if(grounding.contains("SIM_JACCARD_ITEMS") || grounding.contains("SIM_LASTFM_ITEMS") || grounding.contains("SIM_TAGS_ITEMS")){	//this means that this is an item-based style explanation
    			if(grounding.contains("SIM_JACCARD_ITEMS")){	//this means that this is an item-based style explanation
    				//now I need to find the similar CF item
    				String[] items = grounding.split("_ITEMS\\(")[1].split("\\)")[0].split(",");
    				//also get the value of this predicate (to figure out how similar this item is in case similarities are not binarized)
    				Double similarity = Double.parseDouble(grounding.split("_ITEMS\\(")[1].split("Value:")[1].split("\t")[0]);
    				String sim_item;
    				//println items[0] + "+" + items[1]
    				//now we need to check which item is the predicted and which one is the similar
    				if(items[0].equals(item))
    					sim_item = items[1]
    				else
    					sim_item =  items[0]
    				
    				//check if the item already exists in the structure with different value of incompatibility/similarity
    				//if this is the case then keep the entry with the lower incom value first
    				//if incompatibility value is the same then keep the entry with the highest similarity
    				if(sim_items_CF.containsKey(sim_item)){
    					//get the current value of incompatibility
    					Double current_incompatibility = Double.parseDouble(sim_items_CF.get(sim_item).split("\t")[1])
    					if(incompatibility<current_incompatibility)
    						sim_items_CF.put(sim_item, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					if(incompatibility==current_incompatibility){
    						Double current_similarity = Double.parseDouble(sim_items_CF.get(sim_item).split("\t")[2])
    						if(similarity>current_similarity)
    							sim_items_CF.put(sim_item, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					}
    				}
    				else
    					sim_items_CF.put(sim_item,weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    				
    					//println "similarity = " + similarity + " sim_items" + sim_items_CF
    			}
    			else if(grounding.contains("SIM_LASTFM_ITEMS")){	//this means that this is an item-based style explanation
    				//now I need to find the similar CF item
    				String[] items = grounding.split("_ITEMS\\(")[1].split("\\)")[0].split(",");
    				//also get the value of this predicate (to figure out how similar this item is in case similarities are not binarized)
    				Double similarity = Double.parseDouble(grounding.split("_ITEMS\\(")[1].split("Value:")[1].split("\t")[0]);
    				String sim_item;
    				//println items[0] + "+" + items[1]
    				//now we need to check which item is the predicted and which one is the similar
    				if(items[0].equals(item))
    					sim_item = items[1]
    				else
    					sim_item =  items[0]
    				
    				//check if the item already exists in the structure with different value of incompatibility/similarity
    				//if this is the case then keep the entry with the lower incom value first
    				//if incompatibility value is the same then keep the entry with the highest similarity
    				if(sim_items_lastfm.containsKey(sim_item)){
    					//get the current value of incompatibility
    					Double current_incompatibility = Double.parseDouble(sim_items_lastfm.get(sim_item).split("\t")[1])
    					if(incompatibility<current_incompatibility)
    						sim_items_lastfm.put(sim_item, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					if(incompatibility==current_incompatibility){
    						Double current_similarity = Double.parseDouble(sim_items_lastfm.get(sim_item).split("\t")[2])
    						if(similarity>current_similarity)
    							sim_items_lastfm.put(sim_item, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					}
    				}
    				else
    					sim_items_lastfm.put(sim_item,weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    				
    					//println "similarity = " + similarity + " sim_items" + sim_items_CF
    			}
    			else if(grounding.contains("SIM_COSINE_USERS") || grounding.contains("SIM_JACCARD_USERS")){	//this means that this is a user-based style explanation
    				//now I need to find the similar user
    				String[] users = grounding.split("_USERS\\(")[1].split("\\)")[0].split(",");
    				Double similarity = Double.parseDouble(grounding.split("_USERS\\(")[1].split("Value:")[1].split("\t")[0]);
    				String sim_user;
    				//println items[0] + "+" + items[1]
    				//now we need to check which item is the predicted and which one is the similar
    				if(users[0].equals(user))
    					sim_user = users[1]
    				else
    					sim_user = users[0]
    				
    				if(sim_users_CF.containsKey(sim_user)){
    					//get the current value of incompatibility
    					Double current_incompatibility = Double.parseDouble(sim_users_CF.get(sim_user).split("\t")[1])
    					if(incompatibility<current_incompatibility)
    						sim_users_CF.put(sim_user, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					if(incompatibility==current_incompatibility){
    						Double current_similarity = Double.parseDouble(sim_users_CF.get(sim_user).split("\t")[2])
    						if(similarity>current_similarity)
    							sim_users_CF.put(sim_user, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					}
    				}
    				else
    					sim_users_CF.put(sim_user,weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    				//sim_users_CF.put(sim_user,weight+"\t"+incompatibility+"\t"+similarity)
    				//println "similarity = " + similarity + " sim_users" + sim_users_CF
    			}
    			else if(grounding.contains("USERS_ARE_FRIENDS")){	//social-based explanations
    				//now I need to find the friend
    				String[] friends = grounding.split("USERS_ARE_FRIENDS\\(")[1].split("\\)")[0].split(",");
    				Double similarity = Double.parseDouble(grounding.split("USERS_ARE_FRIENDS\\(")[1].split("Value:")[1].split("\t")[0]);
    				String friend;
    				//println items[0] + "+" + items[1]
    				//now we need to check which item is the predicted and which one is the similar
    				if(friends[0].equals(user))
    					friend = friends[1]
    				else
    					friend = friends[0]
    					
    				if(sim_friends.containsKey(friend)){
    					//get the current value of incompatibility
    					Double current_incompatibility = Double.parseDouble(sim_friends.get(friend).split("\t")[1])
    					if(incompatibility<current_incompatibility)
    						sim_friends.put(friend, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					if(incompatibility==current_incompatibility){
    						Double current_similarity = Double.parseDouble(sim_friends.get(friend).split("\t")[2])
    						if(similarity>current_similarity)
    							sim_friends.put(friend, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					}
    				}
    				else
    					sim_friends.put(friend,weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    				//sim_friends.put(friend,weight+"\t"+incompatibility+"\t"+similarity)
    				//println "similarity = " + similarity + " sim_friends" + sim_friends
    			}
    			else if(grounding.contains("SIM_TAGS_ITEMS")){	//content-based style based on tags
    				//now I need to find the similar content item
    				String[] items = grounding.split("SIM_TAGS_ITEMS\\(")[1].split("\\)")[0].split(",");
    				Double similarity = Double.parseDouble(grounding.split("SIM_TAGS_ITEMS\\(")[1].split("Value:")[1].split("\t")[0]);
    				String sim_item;
    				//println items[0] + "+" + items[1]
    				//now we need to check which item is the predicted and which one is the similar
    				if(items[0].equals(item))
    					sim_item = items[1]
    				else
    					sim_item = items[0]
    				
    				if(sim_items_content.containsKey(sim_item)){
    					//get the current value of incompatibility
    					Double current_incompatibility = Double.parseDouble(sim_items_content.get(sim_item).split("\t")[1])
    					if(incompatibility<current_incompatibility)
    						sim_items_content.put(sim_item, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					if(incompatibility==current_incompatibility){
    						Double current_similarity = Double.parseDouble(sim_items_content.get(sim_item).split("\t")[2])
    						if(similarity>current_similarity)
    							sim_items_content.put(sim_item, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    					}
    				}
    				else
    					sim_items_content.put(sim_item,weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
    				//sim_items_content.put(sim_item, weight+"\t"+incompatibility+"\t"+similarity)
    				//println "similarity = " + similarity + " sim_content_items" + sim_items_content
    			}
    			else if(grounding.contains("ITEM_HAS_TAG")){
					//now I need to find the similar content item
    				String tag = grounding.split("ITEM_HAS_TAG\\(")[1].split("\\)")[0].split(",")[1];
					//continue only if this tag is not an offensive one
					if(!offensiveTags.contains(tag)){
	    				Double similarity = Double.parseDouble(grounding.split("ITEM_HAS_TAG\\(")[1].split("Value:")[1].split("\t")[0]);
	    				
	    				if(tags.containsKey(tag)){
	    					//get the current value of incompatibility
	    					Double current_incompatibility = Double.parseDouble(tags.get(tag).split("\t")[1])
	    					if(incompatibility<current_incompatibility)
	    						tags.put(tag, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
	    					if(incompatibility==current_incompatibility){
	    						Double current_similarity = Double.parseDouble(tags.get(tag).split("\t")[2])
	    						if(similarity>current_similarity)
	    							tags.put(tag, weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
	    					}
	    				}
	    				else
	    					tags.put(tag,weight+"\t"+incompatibility+"\t"+similarity+"\t"+num_of_predictions)
	    				//sim_items_content.put(sim_item, weight+"\t"+incompatibility+"\t"+similarity)
	    				//println "similarity = " + similarity + " sim_content_items" + sim_items_content
					}
    		    }
    		}
    		
    	}
    	
    	 pruneStructures(item, userID, rating, sim_items_content, sim_friends, sim_items_CF, sim_users_CF, sim_items_lastfm, tags, 
    	 popular_item, THRESHOLD, /*textExplFile,*/ jsonContentCollapsible, jsonContentVenn, 
		 csvContent, csvContentRadial, rating_item_groundings,
		 item_textual, item_jsonCollapsible, item_jsonVenn, item_jsonCluster, item_jsonRadial,
		 user_user_sim, artist_artist_lastfmsim, artist_artist_tagsim, artist_artist_CFsim, friend_friend_sim, tag_popularity,
		 namesToUserIDs, namesToItemIDs, namesToTagIDs);
    	
    }
    
    //given the structures created by the previous function sim_items_content, sim_friends, sim_items_CF, sim_users_CF
    //here we prune explanations based on the criteria (e.g. filter by weight, filter by incompatibility, 
    //filter by maximum number of instances we will show for each rule) 
    //for now I have just one threshold which is the same for all rules 
    //note that for the structure similar users we will not need to do any pruning because we will aggregate the number of sim users
    //we will first prune by incompatibility value and next based on the similarity value
void pruneStructures(String pred_item, 
		String userID,
		Double rating, 
		HashMap<String,String> sim_items_content, 
		HashMap<String,String> sim_friends,
		HashMap<String,String> sim_items_CF, 
		HashMap<String,String> sim_users_CF, 
		HashMap<String,String> sim_items_lastfm,
		HashMap<String,String> tags, 
		String popular_item,
		int THRESHOLD,
		/*def textExplFile,*/
		StringBuilder jsonContentCollapsible, StringBuilder jsonContentVenn,
		StringBuilder csvContent, StringBuilder csvContentRadial,
		TreeMultimap<Double, HashMap<String, HashSet<String>>> rating_item_groundings,
		HashMap<String,String> item_textual,	//it only stores 3 styles and it is for the visuals 
		HashMap<String,String> item_jsonCollapsible, 
		HashMap<String,String> item_jsonVenn,
		HashMap<String,String> item_jsonCluster, 
		HashMap<String,String> item_jsonRadial,
		HashMap<String, Double> user_user_sim,
		HashMap<String, Double> artist_artist_lastfmsim,
		HashMap<String,Double> artist_artist_tagsim,
		HashMap<String,Double> artist_artist_CFsim,
		HashMap<String, Double> friend_friend_sim,
		HashMap<String, Double> tag_popularity,
		HashMap<String, String> namesToUserIDs,
		HashMap<String, String> namesToItemIDs,
		HashMap<String, String> namesToTagIDs){	
    	
		//for the tree multimap 
		final Comparator<Double> DECREASING_DOUBLE_COMPARATOR = Ordering.<Double>natural().reverse().nullsFirst();
		
    	Set<String> jsonChildrenCollapsible = new HashSet();
		Set<String> jsonChildrenVenn = new HashSet();
		ArrayList<String> csvChildren = new ArrayList();
    	csvChildren.add("id,value\n"+ pred_item.replace(".", "").replace(",", ""));	//the header of the csv
		ArrayList<String> csvChildrenRadial = new ArrayList();
		csvChildrenRadial.add("id,value\n"+ pred_item);	//the header of the csv
		
		HashMap<String, HashSet<String>> item_groundings = new HashMap(); //initialize the structure where we will keep the item_groundings		
		HashSet<String> groundings = new HashSet(); //initialize the structure where we will keep the groundings
		
		String textual_output = "We recommend to you the artist <bold>" + pred_item +  "</bold> because:\\\\n"; //this is to store the textual explanation with the 3 styles
		int expl_counter=1;
		
		//here we will choose which 3 styles of explanations we will keep for the Venn diagrams and the other visuals
		Boolean sim_items_content_exist = false, 
					sim_friends_exist = false, 
					sim_items_CF_exist = false, 
					sim_users_CF_exist = false,
					sim_items_lastfm_exist = false, 
					tags_exist = false, 
					popular_item_exist = false;
					
		int num_of_styles=0;
		if(!sim_items_CF.isEmpty()){
			sim_items_CF_exist = true;
			num_of_styles++;
		}
		if(!sim_items_lastfm.isEmpty()){
			sim_items_lastfm_exist = true;
			num_of_styles++;
		}
		if(popular_item!=null){
			popular_item_exist = true;
			num_of_styles++;
		}
		
		
		if(num_of_styles<3){	  //here I need to make true 1,2,or 3 other styles
			if(!tags.isEmpty()){  //first put the tags
				tags_exist=true;
				num_of_styles++;
			}					
		}
		if(num_of_styles<3){	  //here I need to make true 1,2,or 3 other styles
			if(!sim_users_CF.isEmpty()){  
				sim_users_CF_exist=true;
				num_of_styles++;
			}
		}
		if(num_of_styles<3){	  //here I need to make true 1,2,or 3 other styles
			if(!sim_items_content.isEmpty()){
				sim_items_content_exist=true;
				num_of_styles++;
			}
		}
		if(num_of_styles<3){	  //here I need to make true 1,2,or 3 other styles
			if(!sim_friends.isEmpty()){
				sim_friends_exist=true;
				num_of_styles++;
			}
		}
		
		println "For item " + pred_item + " Num of styles = " + num_of_styles;
		println "For item " + pred_item + " sim_items_content_exist = " + sim_items_content_exist + 
				" sim_friends_exist = " + sim_friends_exist + " sim_items_CF_exist = " + sim_items_CF_exist +
				" sim_users_CF_exist = " + sim_users_CF_exist + " sim_items_lastfm_exist = " + sim_items_lastfm_exist + 
				" tags_exist = " + tags_exist + " popular_item_exist = " + popular_item_exist;
				
		
		String expl;
		if(!sim_items_content.isEmpty()){
			//println "---About to call the prune function for the sim_items_content---";
			//prune to keep just THRESHOLD items to show 
			pruneBasedOnSimilarityValue(pred_item, sim_items_content, namesToItemIDs, artist_artist_tagsim, THRESHOLD)
			
			//print the items
    		if(!sim_items_content.isEmpty()){
				if(sim_items_content.size()==1){
    				expl = /*"Content-based: " + */pred_item + " has similar tags with the artist: " + sim_items_content.keySet() + " that is in your profile."
    				//print expl;
    				//textExplFile.append(expl);
					groundings.add(expl);
    			}
    			else{
    				expl = /*"Content-based: " + */pred_item + " has similar tags with the artists: " + sim_items_content.keySet()  + " that are in your profile.";
    				//print expl 
    				//textExplFile.append(expl);
					groundings.add(expl);
    			}
				if(sim_items_content_exist){
					String legend = "Has similar tags with artists";
					String shortLegend = "Similar tags"
					jsonChildrenCollapsible.add(createJsonChild(legend, sim_items_content.keySet())); //update the structure for the json
					jsonChildrenVenn.add(createJsonChild(legend, sim_items_content.keySet()));
					csvChildren.add(pred_item.replace(".", "").replace(",", "") + "." + legend);
					addCsvEntry(csvChildren, pred_item, legend, sim_items_content.keySet()); //update the string for the csv
					csvChildrenRadial.add(pred_item + "." + shortLegend);
					addCsvEntry(csvChildrenRadial, pred_item, shortLegend, sim_items_content.keySet()); //update the string for the csv
					textual_output += expl_counter.toString() + "." + expl + ".\\\\n"
					expl_counter++;
				}
    		}
    	}
    	if(!sim_friends.isEmpty()){
			//println "---About to call the prune function for the friend_friend_sim---"
			//prune to keep jsu THRESHOLD items to show
			pruneBasedOnSimilarityValue(userID, sim_friends, namesToUserIDs, friend_friend_sim, THRESHOLD)
			
    		if(!sim_friends.isEmpty()){
    			//String legend = "The following friends of yours like " + pred_item 
				
				if(sim_friends.size()==1){
    				expl = /*"Friend-based: */ "Your friend " + sim_friends.keySet() + " likes artist " + pred_item;
    				//print expl;
    				//textExplFile.append(expl);
					groundings.add(expl);
    			}
    			else{
    				expl = /*"Friend-based: */ "Your friends " + sim_friends.keySet() + " like artist " + pred_item;
    				//for(String item : sim_friends.keySet())
    				//	expl = expl + item + ", "
    				//expl = expl + " also like artist " + pred_item + ".\n";
    				//print expl;
    				//textExplFile.append(expl);
					groundings.add(expl);
    			}
				if(sim_friends_exist){
					String legend = "Friends that like this artist"
					String shortLegend = "Friends"
					jsonChildrenCollapsible.add(createJsonChild(legend, sim_friends.keySet()));
					jsonChildrenVenn.add(createJsonChild(legend, sim_friends.keySet()));
					csvChildren.add(pred_item.replace(".", "").replace(",", "") + "." + legend);
					addCsvEntry(csvChildren, pred_item, legend, sim_friends.keySet()); //update the string for the csv
					csvChildrenRadial.add(pred_item + "." + shortLegend);
					addCsvEntry(csvChildrenRadial, pred_item, shortLegend, sim_friends.keySet()); //update the string for the csv
					textual_output += expl_counter.toString() + "." + expl + ".\\\\n"
					expl_counter++;
				}
    		}
    	}
    	if(!sim_items_CF.isEmpty()){
			//println "---About to call the prune function for the artist_artist_CFsim---"
			pruneBasedOnSimilarityValue(pred_item, sim_items_CF, namesToItemIDs, artist_artist_CFsim, THRESHOLD)
				
    		if(!sim_items_CF.isEmpty()){
    			//String legend =  "is similar to the following artists that you like"
				
				if(sim_items_CF.size()==1){
    				//String expl = "Item-based-CF: Artist " + pred_item + " is similar to the artist " + sim_items_CF.keySet() +  " that you like.\n";
    				expl = /*"Item-based-CF:*/ "People who listen to " + sim_items_CF.keySet()  + " also listen to " + pred_item +  " and you listen to " + sim_items_CF.keySet();
    				//print expl;
    				//textExplFile.append(expl);
					groundings.add(expl);
    			}
    			else{
    				//String expl = "Item-based-CF: Artist " + pred_item + " is similar to the artists " + sim_items_CF.keySet() +  " that you like.\n";
    				expl = /*"Item-based-CF: */ "People who listen to " + sim_items_CF.keySet()  + " also listen to " + pred_item +  " and you listen to " + sim_items_CF.keySet();
    				//for(String item : sim_items_CF.keySet())
    				//	expl = expl + item + ", "
    				//expl = expl +  " are similar to item " + pred_item + ".\n"
    				//print expl;
    				//textExplFile.append(expl);
					groundings.add(expl);
    			}
				if(sim_items_CF_exist){
					String legend = "People who listen to this artist also listen";
					String shortLegend = "People also listen";
					jsonChildrenCollapsible.add(createJsonChild(legend, sim_items_CF.keySet()));
					jsonChildrenVenn.add(createJsonChild(legend, sim_items_CF.keySet()));
					csvChildren.add(pred_item.replace(".", "").replace(",", "") + "." + shortLegend);
					addCsvEntry(csvChildren, pred_item, shortLegend, sim_items_CF.keySet()); //update the string for the csv
					csvChildrenRadial.add(pred_item + "." + shortLegend);
					addCsvEntry(csvChildrenRadial, pred_item, shortLegend, sim_items_CF.keySet()); //update the string for the csv
					textual_output += expl_counter.toString() + "." + expl + ".\\\\n"
					expl_counter++;
				}
    		}
    		
    	}
    	if(!sim_items_lastfm.isEmpty()){
			//println "---About to call the prune function for the artist_artist_lastfmsim--"
			pruneBasedOnSimilarityValue(pred_item, sim_items_lastfm, namesToItemIDs, artist_artist_lastfmsim, THRESHOLD)
			
			if(!sim_items_lastfm.isEmpty()){
    			//String legend =  "According to lastfm " + pred_item + " is similar to the following artists"
				
				if(sim_items_lastfm.size()==1){
    				expl = /*"Item-based-lastfm: */ "According to lastfm artist " + pred_item + " is similar to artist " + sim_items_lastfm.keySet() + " that is in your profile";
    				//print expl;
    				//textExplFile.append(expl);
					groundings.add(expl);
    			}
    			else{
    				expl = /*"Item-based-lastfm: */ "According to lastfm artist " + pred_item + " is similar to artists " + sim_items_lastfm.keySet() + " that are in your profile"
    				//for(String item : sim_items_CF.keySet())
    				//	expl = expl + item + ", "
    				//expl = expl +  " are similar to item " + pred_item + ".\n"
    				//print expl;
    				//textExplFile.append(expl);
					groundings.add(expl);
    			}
				if(sim_items_lastfm_exist){
					String legend =  "Similar to the artists"
					String shortLegend =  "Similar to"
					jsonChildrenCollapsible.add(createJsonChild(legend, sim_items_lastfm.keySet()));
					jsonChildrenVenn.add(createJsonChild(legend, sim_items_lastfm.keySet()));
					csvChildren.add(pred_item.replace(".", "").replace(",", "") + "." + legend);
					addCsvEntry(csvChildren, pred_item, legend, sim_items_lastfm.keySet()); //update the string for the csv
					csvChildrenRadial.add(pred_item + "." + shortLegend);
					addCsvEntry(csvChildrenRadial, pred_item, shortLegend, sim_items_lastfm.keySet()); //update the string for the csv
					textual_output += expl_counter.toString() + "." + expl + ".\\\\n"
					expl_counter++;
				}
    		}
    		
    	}
    	if(!tags.isEmpty()){
			
			pruneBasedOnTagPopularity(tags, namesToTagIDs, tag_popularity, THRESHOLD)
			
    		if(!tags.isEmpty()){
    			//String legend =  "Has the following tags that you like"
				
				if(tags.size()==1){
    				expl = /*"Tags: */ "Artist " + pred_item + " has the tag " + tags.keySet() + " that is in your profile" 
    				//print expl;
    				//textExplFile.append(expl);
					groundings.add(expl);
    				//println "Tags: Artist " + pred_item + " has the tag " + tags.keySet() + " that you like."
    			}
    			else{
    				expl = /*"Tags: */ "Artist " + pred_item + " has the tags " + tags.keySet() + " that are in your profile"
    				//for(String tag : tags.keySet())
    				//	expl = expl + tag + ", "
    				//expl = expl + " that you like.\n"
    				//print expl;
    				//textExplFile.append(expl);
					groundings.add(expl);
    			}
				if(tags_exist){
					String legend =  "Has tags"
					String shortLegend =  "Has tags"
					jsonChildrenCollapsible.add(createJsonChild(legend, tags.keySet()));
					jsonChildrenVenn.add(createJsonChild(legend, tags.keySet()));
					csvChildren.add(pred_item.replace(".", "").replace(",", "") + "." + legend);
					addCsvEntry(csvChildren, pred_item, legend, tags.keySet()); //update the string for the csv
					csvChildrenRadial.add(pred_item + "." + shortLegend);
					addCsvEntry(csvChildrenRadial, pred_item, shortLegend, tags.keySet()); //update the string for the csv
					textual_output += expl_counter.toString() + "." + expl + ".\\\\n"
					expl_counter++;
				}
    		}
    	}
    	if(!sim_users_CF.isEmpty()){	//for similar users
			//println "---About to call the prune function for the user_user_sim---"
			//prune to keep just THRESHOLD items to show
			pruneBasedOnSimilarityValue(userID, sim_users_CF, namesToUserIDs, user_user_sim, THRESHOLD)
			
			if(!sim_users_CF.isEmpty()){
				
				if(sim_users_CF.size()==1){
					expl = /*"User-based-CF:*/ "Lastfm user " + sim_users_CF.keySet() + " with whom you share similar music tastes, listens to the artist " + pred_item;
					//print expl;
					//textExplFile.append(expl);
					groundings.add(expl);
				}
				else{
					expl = /*"User-based-CF: */ "Lastfm users " + sim_users_CF.keySet() + " with whom you share similar music tastes, listen to the artist " + pred_item;
					//for(String item : sim_users_CF.keySet())
					//	expl = expl + item + ", "
					//expl = expl + " with whom you share similar music tastes, also like the artist " + pred_item + ".\n"
					//print expl;
					//textExplFile.append(expl);
					groundings.add(expl);
				}
				//String legend =  "The following lastfm users with whom you share similar music tastes like " + pred_item;
				if(sim_users_CF_exist){
					String legend = "Users with similar tastes that like this artist"
					String shortLegend =  "Similar users";
					jsonChildrenCollapsible.add(createJsonChild(legend, sim_users_CF.keySet()));
					jsonChildrenVenn.add(createJsonChild(legend, sim_users_CF.keySet()));
					csvChildren.add(pred_item.replace(".", "").replace(",", "") + "." + legend);
					addCsvEntry(csvChildren, pred_item, legend, sim_users_CF.keySet()); //update the string for the csv
					csvChildrenRadial.add(pred_item + "." + shortLegend);
					addCsvEntry(csvChildrenRadial, pred_item, shortLegend, sim_users_CF.keySet()); //update the string for the csv
					textual_output += expl_counter.toString() + "." + expl + ".\\\\n"
					expl_counter++;
				}
			}
    		
    	}
    	if(popular_item!=null){
			//to print the doubles with 2 decimal digits
			DecimalFormat df2 = new DecimalFormat(".##");
    		//String legend =  "Artist " + pred_item + " is very popular in the lastfm database with the following statistics";
			Integer listeners  = Integer.parseInt(popular_item.split("\t")[0]);
    		Integer playcount = Integer.parseInt(popular_item.split("\t")[1]);
    		
			Double incompatibility = Double.parseDouble(popular_item.split("\t")[3]);
			//String expl = "Artist " + pred_item + " is very popular in the lastfm database with number of listeners = " + df2.format(listeners/1000000.0) + " M and playcounts = " +  df2.format(playcount/1000000.0) + " M.\n";
			expl = "Artist " + pred_item + " is very popular in the lastfm database with " + df2.format(listeners/1000000.0)  + " M listeners and " +  df2.format(playcount/1000000.0) + " M playcounts";
			
			if(popular_item_exist){
				String legend =  "Popular artist";
				String shortLegend =  "Popular";
				jsonChildrenCollapsible.add(createJsonCollapsibleChildPopularity(legend, popular_item.split("\t")[0], popular_item.split("\t")[1]));
				jsonChildrenVenn.add(createJsonVennChildPopularity(legend, popular_item.split("\t")[0], popular_item.split("\t")[1]));
				csvChildren.add(pred_item.replace(".", "").replace(",", "") + "." + legend);
				addCsvEntryPopularity(csvChildren, pred_item, legend, popular_item.split("\t")[0], popular_item.split("\t")[1]); //update the string for the csv
				csvChildrenRadial.add(pred_item + "." + shortLegend);
				addCsvEntryPopularity(csvChildrenRadial, pred_item, shortLegend, popular_item.split("\t")[0], popular_item.split("\t")[1]); //update the string for the csv
				textual_output += expl_counter.toString() + "." + expl + ".\\\\n"
				expl_counter++;
			}
			
			//print expl;
			//textExplFile.append(expl);
			groundings.add(expl);
    	}
    	
    	//create the root of the json file
    //	for(String jsonChild : jsonChildren){
    //		println jsonChild;
    //	}
    	
		println "---pruneStructures---"
		println "Item " + pred_item + " groundings = " + textual_output;
		
		item_textual.put(pred_item, textual_output);
		
		//jsonContent = new StringBuilder(createJsonRoot(pred_item, jsonChildren));
		jsonContentCollapsible.append(createJsonRoot(pred_item, jsonChildrenCollapsible));
		//println "\njson Content Collapsible" + jsonContent;
		//copy this format to the structure 
		item_jsonCollapsible.put(pred_item, jsonContentCollapsible.toString());

		jsonContentVenn.append(createJsonRoot(pred_item, jsonChildrenVenn));
		//println "\njson Content Venn" + jsonContent;
		//copy this format to the structure
		item_jsonVenn.put(pred_item, jsonContentVenn.toString());

		//csvContent = new StringBuilder(createCsv(csvChildren));
		csvContent.append(createCsv(csvChildren));
		//println "\ncsv Content = " + csvContent; 
		//copy this to the structure 
		item_jsonCluster.put(pred_item, csvContent.toString());
		
		//csvContent = new StringBuilder(createCsv(csvChildren));
		csvContentRadial.append(createCsv(csvChildrenRadial));
		//println "\ncsv ContentRadial = " + csvContentRadial;
		//copy this to the structure
		item_jsonRadial.put(pred_item, csvContentRadial.toString());
		
		//update the structure that keeps the item along with each groundings
		item_groundings.put(pred_item, groundings);
		//finally update the entry in the general tree-multi-map
		rating_item_groundings.put(rating, item_groundings);
		
		
}
 
//this function prunes based on the similarity value - at the end, the number of instantiations will be equal to the THRESHOLD	
//CAUTION: for the case of users the first argument is userID (and not pred_item)
//also the second argument has sim_users, the third has argument namesToUserIDs
//and the last has user_user_sim or friend_friend_sim
void pruneBasedOnSimilarityValue(String pred_item, HashMap<String,String> sim_items, HashMap<String, String> namesToItemIDs, 
								 HashMap<String, Double> artist_artist_sim, Integer THRESHOLD){
	
	final Comparator<Double> DECREASING_DOUBLE_COMPARATOR = Ordering.<Double>natural().reverse().nullsFirst();
								 
	//each entry of the structure sim_items_content is of the form
	//<item,weight\tincompatibility\tsimilarity\tnumber_of_predictions>
	if(sim_items.size()>THRESHOLD){
//		println "sim_items = " + sim_items.keySet();
//		println "new size of sim_items = " + sim_items.size() + " need to prune ..."
		//use the structure artist_artist_sim
		//treemultimap to keep the top <threshold entries>
		TreeMultimap<Double, String> rating_item = new TreeMultimap<>(DECREASING_DOUBLE_COMPARATOR, Ordering.natural());
		//put all the entries in the structure rating_item so as to have them sorted
		for(Iterator<Map.Entry<String, String>> iter = sim_items.entrySet().iterator(); iter.hasNext();){
			Map.Entry<String, String> entry = iter.next();
			//get the uniqueIDS from the structure namesToItemIDs
			if(!namesToItemIDs.containsKey(pred_item)){
				println "[ERROR] in pruneBasedOnSimilarityValue: item or user " + pred_item + " is not contained in the structure namesToItemIDs or namesToUserIDs"
			}
			else if (!namesToItemIDs.containsKey(entry.getKey())){
				println "[ERROR] in pruneBasedOnSimilarityValue: item or user " + entry.getKey() + " is not contained in the structure namesToItemIDs or namesToUserIDs"
			}
			else{
				String item_item = namesToItemIDs.get(pred_item) + "_" + namesToItemIDs.get(entry.getKey());
				Double sim;
				//Double sim = Double.parseDouble(entry.getValue().split("\t")[2])
				//retrieve the similarity from the structure artist_artist_tagsim
				if(!artist_artist_sim.containsKey(item_item)){
					println "[ERROR]: in pruneBasedOnSimilarityValue The items " + item_item + " are not in the structure artist_artist_sim!";
					sim = 0;
				}
				else{
					sim = artist_artist_sim.get(item_item);
				}
				//println "sim = " + sim
				//if the size is larger than threshold then we need to prune
				if(rating_item.size()>=THRESHOLD){
					//insert this only if the value is larger than the smallest value in the structure rating_item
					Double lowest_sim = rating_item.asMap().lastKey();
					//println "lowest_sim = " + lowest_sim
					if(lowest_sim < sim){
						//in case we have more than one entries with the same value lowest sim then we delete randomly the first one
						NavigableSet<String> lowest_sims = rating_item.get(lowest_sim)
						String first_element = lowest_sims.pollFirst();
						rating_item.remove(lowest_sim, first_element)//delete only one entry 
						rating_item.put(sim, item_item)//insert the new one
						//println "delete and insert to structure - current size of rating_item = " + rating_item.size();
					}
				}
				else{	//if the size is smaller then just insert this element to the structure
					rating_item.put(sim, item_item)
					//println "inserted to structure - current size of rating_item = " + rating_item.size();
				}
			}
			
		}
//		for(Double key : rating_item.keySet()){
//			println "rating item key " + key + " values " + rating_item.get(key)
//		}
		//println "Size of sim_items = " + sim_items.size()
		//at this point the size of the structure rating_item should be at most threshold
		//iterate again over the sim_items_content
		//if the entry exists in the rating_item then keep it otherwise delete it
		for(Iterator<Map.Entry<String, String>> iter = sim_items.entrySet().iterator(); iter.hasNext();){
			Map.Entry<String, String> entry = iter.next();
			//String item_item = pred_item + "_" + entry.getKey();
			if(!namesToItemIDs.containsKey(pred_item) || !namesToItemIDs.containsKey(entry.getKey())){
				println "[ERROR] in pruneBasedOnSimilarityValue: one of the two items " + pred_item + " or " + entry.getKey() + " is not contained in the structure namesToItemIDs"
			}
			else{
				String item_item = namesToItemIDs.get(pred_item) + "_" + namesToItemIDs.get(entry.getKey());
				//Double sim = Double.parseDouble(entry.getValue().split("\t")[2])
				if(!artist_artist_sim.containsKey(item_item)){
					println "[ERROR] in pruneBasedOnSimilarityValue: the combination " + item_item + " does not exist in the structure artist_artist_sim";
				}
				else{
					Double sim = artist_artist_sim.get(item_item);
					//println "item_item " +  item_item + " sim " + sim
					//if it is not contained in the rating_item then delete it
					if(!rating_item.containsEntry(sim,item_item)){
						iter.remove();
					}
				}
			}
		}
//		println "Finally new size of sim_items_content = " + sim_items.size()
//		for(String key : sim_items.keySet()){
//			println "Finally sim_items_content key = " + key + " value " + sim_items.get(key)
//		}
	}
}
		
 //this function prunes based on the similarity value - at the end, the number of instantiations will be equal to the THRESHOLD
 void pruneBasedOnTagPopularity(HashMap<String,String> tags, HashMap<String, String> namesToTagIDs,
								  HashMap<String, Double> tag_popularity, Integer THRESHOLD){
	 
	 final Comparator<Double> DECREASING_DOUBLE_COMPARATOR = Ordering.<Double>natural().reverse().nullsFirst();
			
	 //println "print namesToTags "
	 //println namesToTagIDs.keySet();
	 					  
	 
	 //each entry of the structure sim_items_content is of the form
	 //<item,weight\tincompatibility\tsimilarity\tnumber_of_predictions>
	 if(tags.size()>THRESHOLD){
		 //println "sim_items = " + tags.keySet();
		 //println "new size of sim_items = " + tags.size() + " need to prune ..."
		 //use the structure tags
		 //treemultimap to keep the top <threshold entries>
		 TreeMultimap<Double, String> rating_item = new TreeMultimap<>(DECREASING_DOUBLE_COMPARATOR, Ordering.natural());
		 //put all the entries in the structure rating_item so as to have them sorted
		 for(Iterator<Map.Entry<String, String>> iter = tags.entrySet().iterator(); iter.hasNext();){
			 Map.Entry<String, String> entry = iter.next();
			 String current_tag = entry.getKey();
			 //println "Current tag = " + current_tag;
			 //get the uniqueIDS from the structure namesToItemIDs
			 if (!namesToTagIDs.containsKey(current_tag)){
				 println "[ERROR] in pruneBasedOnTagPopularity: tag " + current_tag + " is not contained in the structure namesToTagIDs"
			 }
			 else{
				 Double popularity;
				 //Double sim = Double.parseDouble(entry.getValue().split("\t")[2])
				 //retrieve the similarity from the structure artist_artist_tagsim
				 if(!tag_popularity.containsKey(current_tag)){
					 //println "[INF]: in pruneBasedOnTagPopularity The tag " + current_tag + " is not in the structure tag_popularity! popularity = 0";
					 popularity = 0;
				 }
				 else{
					 popularity = tag_popularity.get(current_tag);
				 }
				 //println "popularity  = " + popularity
				 //if the size is larger than threshold then we need to prune
				 if(rating_item.size()>=THRESHOLD){
					 //insert this only if the value is larger than the smallest value in the structure rating_item
					 Double lowest_pop = rating_item.asMap().lastKey();
					 //println "lowest_pop = " + lowest_pop
					 if(lowest_pop < popularity){
						 //in case we have more than one entries with the same value lowest sim then we delete randomly the first one
						 NavigableSet<String> lowest_sims = rating_item.get(lowest_pop)
						 String first_element = lowest_sims.pollFirst();
						 rating_item.remove(lowest_pop, first_element)//delete only one entry
						 rating_item.put(popularity, current_tag)//insert the new one
						 //println "delete and insert to structure - current size of rating_item = " + rating_item.size();
					 }
				 }
				 else{	//if the size is smaller then just insert this element to the structure
					 rating_item.put(popularity, current_tag)
					 //println "inserted to structure - current size of rating_item = " + rating_item.size();
				 }
			 }
			 
		 }
//		 for(Double key : rating_item.keySet()){
//			 println "rating item key " + key + " values " + rating_item.get(key)
//		 }
//		 println "Size of tags = " + tags.size()
		 //at this point the size of the structure rating_item should be at most threshold
		 //iterate again over the sim_items_content
		 //if the entry exists in the rating_item then keep it otherwise delete it
		 for(Iterator<Map.Entry<String, String>> iter = tags.entrySet().iterator(); iter.hasNext();){
			 Map.Entry<String, String> entry = iter.next();
			 //String item_item = pred_item + "_" + entry.getKey();
			 String current_tag = entry.getKey();
			 if(!namesToTagIDs.containsKey(current_tag)){
				 println "[ERROR] in pruneBasedOnTagPopularity: the tag" + current_tag + " is not contained in the structure namesToTagIDs"
			 	 //iter.remove();
			 }
			 else{
				 //String item_item = namesToTagIDs.get(pred_item) + "_" + namesToTagIDs.get(entry.getKey());
				 //Double sim = Double.parseDouble(entry.getValue().split("\t")[2])
				 if(!tag_popularity.containsKey(current_tag)){
					 //println "[INF] in pruneBasedOnTagPopularity: the current_tag " + current_tag + " does not exist in the structure tag_popularity so we will delete it from the structure";
					 iter.remove();
				 }
				 else{
					 Double popularity = tag_popularity.get(current_tag);
					 //println "current_tag " +  current_tag + " popularity " + popularity
					 //if it is not contained in the rating_item then delete it
					 if(!rating_item.containsEntry(popularity,current_tag)){
						 iter.remove();
					 }
				 }
			 }
			 
		 }
//		 println "Finally new size of sim_items_content = " + tags.size()
//		 for(String key : tags.keySet()){
//			 println "Finally sim_items_content key = " + key + " value " + tags.get(key)
//		 }
	 }
 }
 
										   
    //given the grounded atom this function prints the grounded atom with the String that represents the 
    //user id or business id
    String GenerateGroundedAtom(String atom, HashMap<String, String> userIDsToNames, 
    							HashMap<String, String> itemIDsToNames, HashMap<String, String> tagIDsToNames){
    	
    	String generatedAtom = "";
    	//print "\nAtom = " + atom 
    	//String[] tokens = atom.split("\\W"); //this no longer works in PSL 2.0
    	String[] tokens = atom.replace("'","").split("\\W")
    	//println "\tTokens = " + tokens
    	String user1, user2, item1, item2, tag;
    	
    	if(atom.contains("ITEM_IS_POPULAR")){
    		item1 = itemIDsToNames.get((tokens[1].toInteger()).toString())
    		generatedAtom = tokens[0] + "(" + item1 + ")";
    	}
    	/*
    	else if(atom.contains("RATING_PRIOR")){
    		generatedAtom = tokens[0] ;
    	}
    	*/
    	else if(atom.contains("RATING")){
    		user1 = userIDsToNames.get(tokens[1])
    		item1 = itemIDsToNames.get((tokens[2].toInteger()).toString())
    		generatedAtom = tokens[0] + "(" + user1 + "," + item1 + ")"; 
    	}
    	else if(atom.contains("_ITEMS")){
    		item1 = itemIDsToNames.get((tokens[1].toInteger()).toString())
    		item2 = itemIDsToNames.get((tokens[2].toInteger()).toString())
    		generatedAtom = tokens[0] + "(" + item1 + "," + item2 + ")";
    	}
    	else if(atom.contains("USERS")){
    		user1 = userIDsToNames.get(tokens[1])
    		user2 = userIDsToNames.get(tokens[2])
    		generatedAtom = tokens[0] + "(" + user1 + "," + user2 + ")";
    	}
    	else if(atom.contains("TAG")){
    		item1 = itemIDsToNames.get((tokens[1].toInteger()).toString())
    		tag = tagIDsToNames.get((tokens[2].toInteger()).toString())
    		generatedAtom = tokens[0] + "(" + item1 + "," + tag + ")";
    	}
    	/*	
    	else if(atom.equals("USER")){
    		user1 = userIDsToNames.get(tokens[1])
    		generatedAtom = tokens[0] + "(" + user1 + ")";
    	}
    	else if(atom.equals("ITEM")){
    		item1 = itemIDsToNames.get((tokens[1].toInteger()+add).toString())
    		generatedAtom = tokens[0] + "(" + item1 + ")";
    	}
    	
    	else
    		System.out.println "Error: atom " + atom + " contains a predicate that does not belong to the list."
    	*/
    	return generatedAtom;
    	
    }
    							
    							
    //given the grounded atom this function prints the grounded entities that participate in the 
    //predicate, e.g. given the atom rating(1,11) where 1=bob and 11=metallica
    //will return "Bob","Metallica"
    String GenerateProperties(String atom, HashMap<String, String> userIDsToNames,
    							HashMap<String, String> itemIDsToNames, HashMap<String, String> tagIDsToNames){
    	
    	String generatedProperty = "";
    	//print "\nAtom = " + atom
    	//String[] tokens = atom.split("\\W"); //for some reason this command does not work properly anymore in PSL 2.0 
    	String[] tokens = atom.replace("'","").split("\\W")
    	//println "\tTokens = " + tokens
    	String user1, user2, item1, item2, tag;
    	
    	if(atom.contains("RATING")){
    		user1 = userIDsToNames.get(tokens[1])
    		item1 = itemIDsToNames.get((tokens[2].toInteger()).toString())
    		generatedProperty = "\"" + user1 + "\",\"" + item1 + "\"";
    	}
    	else if(atom.contains("_ITEMS")){
    		item1 = itemIDsToNames.get((tokens[1].toInteger()).toString())
    		item2 = itemIDsToNames.get((tokens[2].toInteger()).toString())
    		generatedProperty = "\"" + item1 + "\",\"" + item2 + "\"";
    	}
    	else if(atom.contains("USERS")){
    		user1 = userIDsToNames.get(tokens[1])
    		user2 = userIDsToNames.get(tokens[2])
    		generatedProperty = "\"" + user1 + "\",\"" + user2 + "\"";
    	}
    	else if(atom.contains("ITEM_HAS_TAG")){
    		item1 = itemIDsToNames.get((tokens[1].toInteger()).toString())
    		tag = tagIDsToNames.get((tokens[2].toInteger()).toString())
    		generatedProperty = "\"" + item1 + "\",\"" + tag + "\"";
    	}
    	/*
    	else if(atom.equals("USER")){
    		user1 = userIDsToNames.get(tokens[1])
    		generatedAtom = tokens[0] + "(" + user1 + ")";
    	}
    	else if(atom.equals("ITEM")){
    		item1 = itemIDsToNames.get((tokens[1].toInteger()+add).toString())
    		generatedAtom = tokens[0] + "(" + item1 + ")";
    	}
    	
    	else
    		System.out.println "Error: atom " + atom + " contains a predicate that does not belong to the list."
    	*/
    	//println "generated property " + generatedProperty
    	return generatedProperty;
    	
    }
    
//function that reads the files with the similarities
void loadSimilarities(HashMap<String, Double> item_item_similarities, String fileName){
	
	//println "About to load the similarities file " + fileName
	
	def ids_names = new File(fileName)
	String item1, item2
	Double similarity;
	def words
	ids_names.eachLine {
		line ->
		words = line.split("\t")
		item1=words[0].toString();
		item2=words[1].toString();
		similarity = Double.parseDouble(words[2])
		//println "line " + item1 + " " + item2 + " " + similarity
		String key = item1 + "_" + item2
		if(item_item_similarities.containsKey(key)){	//if this entry already exist then check its value - if it is less than the current one then replace it
			if(item_item_similarities.get(key) < similarity){	//
				//println "insert item " 
				item_item_similarities.put(key, similarity)
			}
		}
		else{  //if it does not contain this entry then insert it
			item_item_similarities.put(key, similarity)
		}
	}
}

//function that reads the files with the tags
//the first item (first line) is the most popular 
//and the last item (last line) is the least popular
void loadTopTags(HashMap<String, Double> tag_popularity, HashSet<String> offensiveTags, String fileName){
	
	//println "About to load the similarities file " + fileName
	
	def ids_names = new File(fileName)
	String tag
	def words
	int popularity = 1
	ids_names.eachLine {
		line ->
		words = line.split("\t")
		tag=words[0].toString();
		if(!offensiveTags.contains(tag)){	//insert only if this is not contained in the list with the offensive tags
			tag_popularity.put(tag.replace(" ", "_"),popularity)
			popularity++;
		}
	}
}

    							
    //function that reads the structures userID_name, businessID_name, and tagID_name
    void loadData(String fileName, HashMap<String, String> IDsToNames, HashMap<String, String> namesToIDs){
    	
    	//println "About to load the map from users-bussinesses to names"
    	def ids_names = new File(fileName)
    	def words, uniqueID, name
    	ids_names.eachLine {
    		line ->
    		words = line.split("\t")
    		name=words[0].toString().replace("(", "").replace(")","").replace("\"","").replace(".","").replace(",","");
    		uniqueID = words[1].toString();
    		IDsToNames.put(uniqueID,name);	//update the structure uniqueID -> name
			namesToIDs.put(name, uniqueID);	//update the structure name -> uniqueID
    		//int id = words[1].toInteger();
    		//uniqueID = id.toString();
    		//uniqueIDs_names.put(uniqueID,name);
    		
    	}
    	//println "Size of structure IDsToNames = " +  IDsToNames.size();
		//println "Size of structure namesToIDs = " +  namesToIDs.size();
    }

//function that reads the structures userID_name, businessID_name, and tagID_name
void loadTags(String fileName, HashMap<String, String> IDsToNames, HashMap<String, String> namesToIDs, int TAGSTOKEEP){
	
	//println "About to load the map from tags to names"
	def ids_names = new File(fileName)
	def words, uniqueID, name
	ids_names.eachLine {
		line ->
		words = line.split("\t")
		name=words[0].toString().replace("(", "").replace(")","").replace("\"","").replace(".","").replace(",","");
		uniqueID = words[1].toString();
		//insert here only if the tag id is lower than then TAGSTOKEEP
		if(Integer.parseInt(uniqueID)<=TAGSTOKEEP){
			//println "insert tag " + name + " " + uniqueID
			IDsToNames.put(uniqueID,name);	//update the structure uniqueID -> name
			namesToIDs.put(name, uniqueID);	//update the structure name -> uniqueID
			//int id = words[1].toInteger();
			//uniqueID = id.toString();
			//uniqueIDs_names.put(uniqueID,name);
		}
		
	}
	//println "Size of structure IDsToNames = " +  IDsToNames.size();
	//println "Size of structure namesToIDs = " +  namesToIDs.size();
}

	
void loadArtistsData(String fileName, HashMap<String, String> IDsToNames, HashMap<String, String> namesToIDs,
	HashMap<String, String> cleanArtistNameToRealArtistName){
	
	//println "About to load the map from users-bussinesses to names"
	def ids_names = new File(fileName)
	def words, uniqueID, name
	ids_names.eachLine {
		line ->
		words = line.split("\t")
		name=words[0].toString().replace("(", "").replace(")","").replace("\"","").replace(".","").replace(",","");
		uniqueID = words[1].toString();
		IDsToNames.put(uniqueID,name);	//update the structure uniqueID -> name
		namesToIDs.put(name, uniqueID);	//update the structure name -> uniqueID
		cleanArtistNameToRealArtistName.put(name, words[0])
		//int id = words[1].toInteger();
		//uniqueID = id.toString();
		//uniqueIDs_names.put(uniqueID,name);
		
	}
	//println "Size of structure IDsToNames = " +  IDsToNames.size();
	//println "Size of structure namesToIDs = " +  namesToIDs.size();
}

	    
HashMap<String, String>  loadTopArtists(String fileName){
    	
    	//println "About to load the top artists along with the number of listeners and playcounts"
    	HashMap<String, String> artists_stats = new HashMap();
    	
    	def arists = new File(fileName)
    	def words, listeners, playcount
    	arists.eachLine {
    		line ->
    		words = line.split("\t")
    		String artist = words[0].toString().replace("(", "").replace(")","").replace("\"","").replace(".","").replace(",","");
    		listeners=words[1].toString();
    		playcount=words[2].toString();
    		//System.out.println("<" + artist + "," + listeners + "\t" + playcount + ">")
    		artists_stats.put(artist,listeners+"\t"+playcount);
    		
    	}
    	//println "Size of structure = " +  artists_stats.size();
    	
    	return artists_stats;
}
    
    
    //this function is the same for both the id_rule_weight and the id_relation_entity1_entity2
    def createFile(String fileName){
    	//file where we will store the results
    	def resultsFile;
    	if(fileName!=null){
    		resultsFile = new File(fileName);
    		if(!resultsFile.createNewFile()){	//if the file already exists then delete it and create it
    			resultsFile.delete();
    			resultsFile.createNewFile();
    		}
    	}
    	return resultsFile;
    }
    //compute how many times a string belongs to a string
    //how many time the string "RATING" belongs to each rule
    int findOccurencesOfRATING(String rule){
    	String findStr = "RATING";
    	int lastIndex = 0;
    	int count = 0;
    	while(lastIndex != -1){
    		lastIndex = rule.indexOf(findStr,lastIndex);
    		if(lastIndex != -1){
    			count ++;
    			lastIndex += findStr.length();
    		}
    	}
    	//System.out.println(count);
    	return count;
    }
    
    //to update the structure that keeps the csv file for the cluser and the radial dendrogram
    void addCsvEntry(ArrayList<String> csvContent, String pred_item, String legend, Set<String> instances){
    	for(String instance : instances){
    		csvContent.add(pred_item.replace(".", "").replace(",", "") + "." + legend + "." + instance);
    	}
    }
    
    
    void addCsvEntryPopularity(ArrayList<String> csvContent, String pred_item, String legend, String numOfListeners, String playCounts){
    	csvContent.add(pred_item.replace(".", "").replace(",", "") + "." + legend + "." + "Listeners = " + numOfListeners);
    	csvContent.add(pred_item.replace(".", "").replace(",", "") + "." + legend + "." + "Playcounts = " + playCounts);
    }
	
	String createCsv(ArrayList<String> csvChildren){
		String csvContent;
		int i=1;
		for(String csvChild : csvChildren){
			if(i==1){	//only for the first time
				csvContent = csvChild;
				i++;
			}
			else
				csvContent = csvContent + "\n" + csvChild;
		}
		return csvContent;
	}
    
    //this function creates a Json child. Here is an example of a json child
    /*
       {
            "name": "Friends with Similar Tastes",
            "children": [{
                "name": "George"
            }, {
                "name": "Sam"
            }, 
            {
                "name": "Lise"
            }, {
                "name": "John"
            }]
        }
     */
    //
    String createJsonChild(String name, Set<String> children){
    	
    	String jsonChild;
    	//the title
    	jsonChild = "{\"name\": \"" + name + "\",\"children\":["
    	int sizeOfChildren = children.size();
    	int i=0;
    	for(String child : children){
    		if(i==children.size()-1){//this is the last element where we do not need a comma at the end - instead we need a ]
    			jsonChild += "{\"name\": \"" + child + "\"}]}";
    		}
    		else{//otherwise we need commas
    			jsonChild += "{\"name\": \"" + child + "\"},";
    		}
    		i++;
    	}
    	return jsonChild;
    }
    
    //for the root (i.e., the recommended item only)
    StringBuilder createJsonRoot(String name, Set<String> children){
    	StringBuilder jsonChild = new StringBuilder("{\"name\": \"" + name + "\",\"children\":[");
    	//the title
    	//jsonChild = "{\"name\": \"" + name + "\",\"children\":["
    	int sizeOfChildren = children.size();
    	int i=0;
    	for(String child : children){
    		if(i==children.size()-1){//this is the last element where we do not need a comma at the end - instead we need a ]
    			jsonChild.append(child + "]}");
    		}
    		else{//otherwise we need commas
    			jsonChild.append(child + ", ");
    		}
    		i++;
    	}
    	return jsonChild;
    }
    
    //only for the json Collapsible for the popularity explanation
    StringBuilder createJsonCollapsibleChildPopularity(String name, String numOfListeners, String playCounts){
    	
    	StringBuilder jsonChild = new StringBuilder("{\"name\": \"" + name + "\",\"children\":[");
    	//the title
    	//jsonChild = "{\"name\": \"" + name + "\",\"children\":["
    	jsonChild.append("{\"name\": \"Number of listeners = " + numOfListeners + "\"},");
    	jsonChild.append("{\"name\": \"Playcounts = " + playCounts + "\"}]}");
    		
    	return jsonChild;
    }
    
	//only for the json Venn popularity explanation - put the 3 of the most popular artists in the last.fm database.
	StringBuilder createJsonVennChildPopularity(String name, String numOfListeners, String playCounts){
		
		StringBuilder jsonChild = new StringBuilder("{\"name\": \"" + name + "\",\"children\":[");
		//the title
		//jsonChild = "{\"name\": \"" + name + "\",\"children\":["
		jsonChild.append("{\"name\": \"RadioHead\"},");
		jsonChild.append("{\"name\": \"The Beatles\"},");
		jsonChild.append("{\"name\": \"Queen\"}]}");
			
		return jsonChild;
	}



    public static void main(String[] args) {
        LastFMFinalDebug lfm = new LastFMFinalDebug(null) ;
        lfm.doit(args[0], args[1]);
        // done
    }
}
