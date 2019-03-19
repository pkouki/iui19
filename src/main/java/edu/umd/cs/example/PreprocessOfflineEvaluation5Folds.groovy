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





public class PreprocessOfflineEvaluation5Folds {


    public doit(String md5sfileName, String integratedDirName, String HOME_DIR) {
		
        //keep track of the time
        println "START Time is " + new Date();
		
		ConfigManager cm = ConfigManager.getManager()
		ConfigBundle config = cm.getBundle("basic-example")
		
		def defaultPath = System.getProperty("java.io.tmpdir")
		String dbpath = config.getString("dbpath", defaultPath + File.separator + "rec_sys_lastfm")
		
		//this structure stores entries of the form <sorted_value_of_rating,item> for the user that we run the script
		//for each user we keep at most LIMIT items with the highest values
		final Comparator<Double> DECREASING_DOUBLE_COMPARATOR = Ordering.<Double>natural().reverse().nullsFirst();
		//TreeMultimap<Double, String> rating_item = new TreeMultimap<>(DECREASING_DOUBLE_COMPARATOR, Ordering.natural());
		HashMap<String,TreeMultimap<Double, String>> predicted_user_rating_item = new HashMap();
		
		//this structure stores entries of the form <item,<full_grounding>>
		//for each item we store a set of all the groundings that were generated
		HashMap<String, HashSet<String>> item_grounding = new HashMap();
 
		//load the content of the file to the set
		HashSet<String> md5s = loadUserMD5s(md5sfileName, HOME_DIR);
		
		Integer LIMIT = 50;	//the number of items to keep for each user 
		Integer TAGSTOKEEP = 5;//5; //which tags to keep - for now keep only top 5
		//int VALID_SIZE = 1196; //the size of the users in the validation set
		int VALID_SIZE = 750; //the size of the users in the validation set
		
		        
		int partitionID = 0;	//this id is for creating partitions with unique ids each time
		
		def userDir = HOME_DIR + java.io.File.separator + integratedDirName + java.io.File.separator;
		
        //before doing anything else just load the files which map the user unique ids and the artist uniques ids
        String user_uniqueID_name = "userUniqueIDs"
        String item_uniqueID_name = "artistUniqueIDs"
        String tag_uniqueID_name = "tagUniqueIDs"
        String artists_listeners_playcount = "top_1000_artists"
		String test_artists = "test-artist.names"; 	//this function stores the artists that this user has listened to and we need to put them in the structure to_predict and to_predict_true
       
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
		
		//here define the sets that need to have unique entries.
		HashSet<String> similarities_jaccard_items = new HashSet<String>();
		HashSet<String> similarities_lastfm_items = new HashSet<String>();
		HashSet<String> similarities_tags_items = new HashSet<String>();
		HashSet<String> similarities_cosine_users = new HashSet<String>();
		HashSet<String> similarities_jaccard_users = new HashSet<String>();
		HashSet<String> friends = new HashSet<String>();
		HashSet<String> artists_tags = new HashSet<String>();
		
		HashSet<String> to_predict = new HashSet<String>();			//put the predictions that i need to make here (i.e., the rating predicate of the target partition) This also serves as the ratedTrain blocking predicate 
		HashSet<String> to_predict_true = new HashSet<String>(); 	//for the evaluation: each line contains user item and rating (0 or 1)
		HashSet<String> rating_train = new HashSet<String>();		//the training data, i.e., the rating predicate for the evidence partition. be careful: the entries from to_predict SHOULD NOT be here as well
		HashSet<String> rated = new HashSet<String>();				//the rated blocking predicate: contains everything that exist in to_predict and rating_train
		HashSet<String> rated_train = new HashSet<String>();		//this is for the predictae ratedTrain and for each user it contains the 20 items that she has rated
		
		HashSet<String> users = new HashSet(); //the file with the users for MyMediaLite
		
		HashSet<String>[] usersVal = new HashSet<String>[5]; // this keeps the users that should be in the validation split
		for(int i=0;i<5;i++){
			usersVal[i] = new HashSet();
		}
		
		HashSet<String>[] usersTest = new HashSet<String>[5]; // this keeps the users that should be in the test split
		for(int i=0;i<5;i++){
			usersTest[i] = new HashSet();
		}
		
		int current_user_id = 0;	
		//here load the data from the different directories and assign unique ids	
		for(String md5 : md5s){
			
			//println "load data for user " + md5
			
			HashMap<String, Integer> initialNameID = new HashMap();
			
			HashSet<String> candidateSet = new HashSet(); //this is the candidate set for each user is used for MyMediaLite
			//it contains everything in the current_to_predict (see below)
			//and the 5 ratings from the current_rating_20
			
			userDir = HOME_DIR + java.io.File.separator + md5 + java.io.File.separator;
			HashMap<Integer, Integer> userIDsToNewUserIDs = loadData(userDir+user_uniqueID_name, userIDsToNames, namesToUserIDs, initialNameID)
			if(userIDsToNewUserIDs==null){
				println "About to move to the next md5 user since userIDsToNewUserIDs is null"
				continue; //move to the next md5 user
			}
			//println "Size of the structure userIDsToNewUserIDs = " + userIDsToNewUserIDs.size()
			//println "userIDsToNewUserIDs = " + userIDsToNewUserIDs;
			
			//initialize the structure again
			initialNameID = new HashMap();
			
			//assign unique ids to the artists
			HashMap<Integer, Integer> itemIDsToNewItemIDs = loadData(userDir+item_uniqueID_name, itemIDsToNames, namesToItemIDs, initialNameID)
			if(itemIDsToNewItemIDs==null){
				println "About to move to the next md5 user since itemIDsToNames is null"
				continue;
			}
			//println "Size of the structure itemIDsToNewItemIDs = " + itemIDsToNewItemIDs.size()
			
			//get the id for this user from the file rated
			String userID = getUserID(userDir + "rated.train", userIDsToNewUserIDs);
			println "Load data for userID = " + userID + " for md5 = " + md5;
			if(userID==null || userID.equals("null")){
				println "Cannot access the userID for this user... About to skip him. "
				continue;
			}
			
			//println "initialNameID size = " + initialNameID.size();
			
			//this function loads the to_predict and to_predict_true with the true ratings
			//CHANGE: this function also takes the rating_train as argument and does the following:
			//if the item is contained already to the rating_train, then it first deletes that entry from the rating_train 
			//and the inserts it into the to_predict structures
			int sizeOfTestArtists = loadToPredictTrue(userID, userDir+test_artists, initialNameID, itemIDsToNewItemIDs, 
							  to_predict, to_predict_true, candidateSet, rated, rating_train);
			
			if(sizeOfTestArtists==0){
				println "User " + userID + " with md5 " + md5 + " has 0 test artists. About to skip him."
				continue;
			}
				
			
			//this function is to post process the crawled data and assign unique ids to everything 
			//so as to be able to integrate the data of all users in the same model 		
			loadDataUniqueIDs(HOME_DIR + java.io.File.separator + integratedDirName, userDir, 
							  userIDsToNewUserIDs, itemIDsToNewItemIDs, users,
							  similarities_jaccard_items, similarities_lastfm_items, similarities_tags_items, 
							  similarities_cosine_users, similarities_jaccard_users,
							  friends, artists_tags,
							  to_predict, to_predict_true, rating_train, rated, rated_train,
							  TAGSTOKEEP, candidateSet, current_user_id, VALID_SIZE, 
							  usersVal, usersTest)
		
			
			current_user_id++;
			
		}
		
		createIntegratedFiles(HOME_DIR + java.io.File.separator + integratedDirName, 
			 users, similarities_jaccard_items,
			 similarities_lastfm_items, similarities_tags_items, similarities_cosine_users,
			 similarities_jaccard_users, friends, artists_tags, 
			 to_predict, to_predict_true, rating_train, rated, rated_train, usersVal, usersTest)
		
		println "Done with creating the files!"
			
		return; 
		                            

    }


//this function loads the to_predict and to_predict_true with the true ratings
//first I need to open the file test-artist.names (each line has the name of the artist that this user has listened)
//I also need to load the names - uniques ids from the file artistUniqueIDs
//then i need for each artist of the test-artist.names to find the id in the artistUniqueIDs
//finally i need to access the structure itemIDsToNewItemIDs to get the id for this artist	
Integer loadToPredictTrue(String userID, String test_artists, HashMap<String, Integer> initialNameID, 
					   HashMap<Integer, Integer> itemIDsToNewItemIDs, 
					   HashSet<String> to_predict, HashSet<String> to_predict_true, HashSet<String> candidateSet,
					   HashSet<String> rated,
					   HashSet<String> rating_train){
	
	//println "About to load to the to_predict and to_predict_true structures the artists that this user has listened to"
	//this function opens the file test-artist.names, read it line by line, and for each entry retrieve the initial id and then the unique id for this
	HashSet<Integer> artistsIDs = getArtistIDs(test_artists, initialNameID, itemIDsToNewItemIDs);
	//println "Size of the structure artistsIDs (should be 5) = " + artistsIDs.size()
	//if(artistsIDs.size()==0)
	if(artistsIDs.size()<5)
		return 0;
	//for each artist, add him to the to_predict and the to_predict_true structures
	for(Integer artistID : artistsIDs){
		
		//first check if the entry is already contrained in the rating_train structure
		//if so then delete it (in order not to have ratings being in both the rating_train AND to_predict)
		if(rating_train.contains(userID + "\t" + artistID)){
			println "The entry " + userID + "\t" + artistID  + " is contained in rating_train so we will delete it!"
			rating_train.remove(userID + "\t" + artistID)
		}
		//sanity check to make sure that i deleted this item (for debugging, then delete this)
		if(rating_train.contains(userID + "\t" + artistID)){
			println "OOOPS . . . The entry " + userID + "\t" + "artistID" + " is STILL contained in rating_train"
		}
		
		to_predict.add(userID + "\t" + artistID)
		to_predict_true.add(userID + "\t" + artistID + "\t1")
		//println userID + "\t" + artistID + "\t1";
		//also update the structure for the candidate set
		candidateSet.add(artistID);
		rated.add(userID + "\t" + artistID);
	}
	//println "current size of the structures to_predict and to_predict_true and candidateSet " + to_predict.size() + " " + to_predict_true.size() + " " + candidateSet.size();
	return artistsIDs.size();		   	
}
	
//helper function for loadToPredictTrue that returns the id of the artist 	
					   
HashSet<Integer> getArtistIDs(String test_artists, HashMap<String, Integer> initialNameID,
							  HashMap<Integer, Integer> itemIDsToNewItemIDs){
	
	HashSet<Integer> artistsIDs = new HashSet();
	//read the file line by line
	def ids = new File(test_artists)
	
	//check if the file exists or not
	if(!ids.exists()){
		println "ERROR: The file " + test_artists + " does not exist!"
		return null;
	}
	Integer id;
	ids.eachLine {
		line ->
			String artistName = line.toString().replace("(", "").replace(")","").replace("\"","").replace(".","").replace(",","");	//retrieve the name of the artist
			if(artistName.contains("unknown")){
				println "Found unknown artist.. About to skip him"
			}
			else{
				//for each artist retrieve the initial id from the structure initialNameID
				if(initialNameID.containsKey(artistName)){
					Integer initialID = initialNameID.get(artistName)
					//after getting the initial id, then retrieve the global id by accessing the structure itemIDsToNewItemIDs
					//if it does not exist there then it means that the id is the same with the initial id
					if(itemIDsToNewItemIDs.containsKey(initialID)){
						id = itemIDsToNewItemIDs.get(initialID)
					}
					else{
						id = initialID
					}
					//println "artist = " + artistName + " id = " + id;
					artistsIDs.add(id);
					
				}
//				else{
//					println "Artist " + artistName + " is not contained in the initial structure initialNameID"
//				}
			}
	}
	return artistsIDs;

}					   			   
					   
	
//compute the size of the structure true_user_rating_item
int computeSizeOfStructure(HashMap<String, HashSet<String>> true_user_rating_item){
	int sizeOfStructure = 0;
	for(String user : true_user_rating_item.keySet()){
		for(String item : true_user_rating_item.get(user)){
			sizeOfStructure++;
		}
	}
	//println "Size of Structure " + sizeOfStructure;
	return sizeOfStructure;
	
}
		
	
//write all the created structures to files so as to process them later in the PSL code 
void createIntegratedFiles(String HOME_DIR, 
						HashSet<String> users,
						HashSet<String> similarities_jaccard_items,
						HashSet<String> similarities_lastfm_items, 
						HashSet<String> similarities_tags_items,
						HashSet<String> similarities_cosine_users, 
						HashSet<String> similarities_jaccard_users,
						HashSet<String> friends, 
						HashSet<String> artists_tags,
						HashSet<String> to_predict, 
						HashSet<String> to_predict_true, 
						HashSet<String> rating_train, 
						HashSet<String> rated,
						HashSet<String> rated_train,
						HashSet<String>[] usersVal,
						HashSet<String>[] usersTest){
	
		println "About to create the file with the users, size = " + users.size()
		writeToFile(HOME_DIR + java.io.File.separator + "users", users, false, 1);			
		
		println "About to create the files usersfoldival and usersfolditest"
		writeUsersValTestFiles(users, usersVal, usersTest, HOME_DIR)
		
		println "About to create the file with the item-item jaccard similarities"		
		writeToFile(HOME_DIR + java.io.File.separator + "artist-artist.user.jaccard.top20.ones", similarities_jaccard_items, true, 1);				
		
		println "About to create the file with the item-item last.fm similarities"
		writeToFile(HOME_DIR + java.io.File.separator + "artist-artist.lastfm.top20.ones", similarities_lastfm_items, true, 1);

		println "About to create the file with the item-item tag similarities"
		writeToFile(HOME_DIR + java.io.File.separator + "artist-artist.tags.jaccard.top20.ones", similarities_tags_items, true, 1);

		println "About to create the file with the user-user cosine similarities"
		writeToFile(HOME_DIR + java.io.File.separator + "inputuser-user.artist.cosine.top20.ones", similarities_cosine_users, true, 1);

		println "About to create the file with the user-user jaccard similarities"
		writeToFile(HOME_DIR + java.io.File.separator + "inputuser-user.artist.jaccard.top20.ones", similarities_jaccard_users, true, 1);

		println "About to create the file with the friends"
		writeToFile(HOME_DIR + java.io.File.separator + "inputuser_friend.graph", friends, false, 1);

		println "About to create the file with the artists-tags"
		writeToFile(HOME_DIR + java.io.File.separator + "artist-tags", artists_tags, false, 1);

//		println "About to create the file with the rated"
//		writeToFile(HOME_DIR + java.io.File.separator + "rated", rated, false, 1);

		println "About to create the file with the rated_train"
		writeToFile(HOME_DIR + java.io.File.separator + "rated_train", rated_train, false, 1);
		
		println "About to create the file with the rating_train"
		writeToFile(HOME_DIR + java.io.File.separator + "rating_train", rating_train, false, 1);
			
		println "About to create the files to_predict and to_predict_true for 5 folds"
		writeToPredictFiles(to_predict_true, usersVal, HOME_DIR)
		
		println "About to create the files rated for 5 folds"
		writeRatedFiles(rated, usersVal, usersTest, HOME_DIR)
		
//		println "About to create the file with the to_predict"
//		writeToFile(HOME_DIR + java.io.File.separator + "to_predict", to_predict, false, 1);
//
//		println "About to create the file to_predict_true"
//		writeToFile(HOME_DIR + java.io.File.separator + "to_predict_true", to_predict_true, false, 1);	
		

}
						
void writeToFile(String fileName, HashSet<String> entries, boolean insertValues, int value){
	
	//first, create the file
	def resultsFile;
	if(fileName!=null){
		resultsFile = new File(fileName);
		if(!resultsFile.createNewFile()){	//if the file already exists then delete it and create it
			resultsFile.delete();
			resultsFile.createNewFile();
		}
	}
	
	for(String entry : entries){
		if(insertValues){	//insert the entry along with the value  
			resultsFile.append(entry + "\t" + value + "\n");
		}
		else{		//insert only the entry
			resultsFile.append(entry + "\n");
		}
	}
	
}	



void writeUsersValTestFiles(HashSet<String> entries, HashSet<String>[] usersVal, 
							HashSet<String>[] usersTest, String HOME_DIR){
	
	//String usersVal1 = "users.fold1.val", usersVal2 = "users.fold2.val", usersVal3 = "users.fold3.val", usersVal4 = "users.fold4.val", usersVal5 = "users.fold5.val";
	//String usersTest1 = "users.fold1.test", usersTest2 = "users.fold2.test", usersTest3 = "users.fold3.test", usersTest4 = "users.fold4.test", usersTest5 = "users.fold5.test";
	
	//create the files for users.fold$i$.val and users.fold$i$.test
	String[] usersValNames = new String[5];
	File[] userValFiles = new File[5];
	
	String[] usersTestNames = new String[5];
	File[] userTestFiles = new File[5];
	
	for(int i=0;i<5;i++){
		usersValNames[i] = "users.fold" + (i+1) + ".val"
		userValFiles[i] = createUsersFile(HOME_DIR + java.io.File.separator + usersValNames[i])
		usersTestNames[i] = "users.fold" + (i+1) + ".test"
		userTestFiles[i] = createUsersFile(HOME_DIR + java.io.File.separator + usersTestNames[i])
	}
	
	for(String entry : entries){
		if(usersVal[0].contains(entry)){
			userValFiles[0].append(entry + "\n");
		}
		else if(usersTest[0].contains(entry)){
			userTestFiles[0].append(entry + "\n");
		}
		
		if(usersVal[1].contains(entry)){
			userValFiles[1].append(entry + "\n");
		}
		else if(usersTest[1].contains(entry)){
			userTestFiles[1].append(entry + "\n");
		}
		
		if(usersVal[2].contains(entry)){
			userValFiles[2].append(entry + "\n");
		}
		else if(usersTest[2].contains(entry)){
			userTestFiles[2].append(entry + "\n");
		}
		
		if(usersVal[3].contains(entry)){
			userValFiles[3].append(entry + "\n");
		}
		else if(usersTest[3].contains(entry)){
			userTestFiles[3].append(entry + "\n");
		}
		
		if(usersVal[4].contains(entry)){
			userValFiles[4].append(entry + "\n");
		}
		else if(usersTest[4].contains(entry)){
			userTestFiles[4].append(entry + "\n");
		}
		
	}
	
}



void writeToPredictFiles(HashSet<String> to_predict_true, HashSet<String>[] usersVal, String HOME_DIR){
	
	//create the files for to_predict.fold$i$.val and to_predict.fold$i$.test
	String[] to_predict_val_filenames = new String[5];
	File[] to_predict_val_files = new File[5];
	
	String[] to_predict_true_val_filenames = new String[5];
	File[] to_predict_true_val_files = new File[5];
	
	String[] to_predict_test_filenames = new String[5];
	File[] to_predict_test_files = new File[5];
	
	String[] to_predict_true_test_filenames = new String[5];
	File[] to_predict_true_test_files = new File[5];
	
	println "About to create the to_predict files"
	
	for(int i=0;i<5;i++){
		to_predict_val_filenames[i] = "to_predict.fold" + (i+1) + ".val"
		to_predict_true_val_filenames[i] = "to_predict_true.fold" + (i+1) + ".val"
		
		to_predict_test_filenames[i] = "to_predict.fold" + (i+1) + ".test"
		to_predict_true_test_filenames[i] = "to_predict_true.fold" + (i+1) + ".test"
		
		to_predict_val_files[i] = createUsersFile(HOME_DIR + java.io.File.separator + to_predict_val_filenames[i])
		to_predict_true_val_files[i] = createUsersFile(HOME_DIR + java.io.File.separator + to_predict_true_val_filenames[i])
		
		to_predict_test_files[i] = createUsersFile(HOME_DIR + java.io.File.separator + to_predict_test_filenames[i])
		to_predict_true_test_files[i] = createUsersFile(HOME_DIR + java.io.File.separator + to_predict_true_test_filenames[i])
	}
	
	//println "print usersVal = " + usersVal;
	//println "print to_predict_true = " + to_predict_true;
	
	for(String entry : to_predict_true){
		
		//println "current entry to predict = " + entry
		
		String[] args = entry.split("\t")
		
		String user = args[0].toString();
		String item = args[1].toString();
		String value = args[2].toString();
		
		//println "user = " + user + " item = " + item + " value = " + value;
		
		if(usersVal[0].contains(user)){
			to_predict_val_files[0].append(user + "\t" + item + "\n");
			to_predict_true_val_files[0].append(user + "\t" + item + "\t" + value + "\n");
		}
		else{
			to_predict_test_files[0].append(user + "\t" + item + "\n");
			to_predict_true_test_files[0].append(user + "\t" + item + "\t" + value + "\n");
		}
		
		if(usersVal[1].contains(user)){
			to_predict_val_files[1].append(user + "\t" + item + "\n");
			to_predict_true_val_files[1].append(user + "\t" + item + "\t" + value + "\n");
		}
		else{
			to_predict_test_files[1].append(user + "\t" + item + "\n");
			to_predict_true_test_files[1].append(user + "\t" + item + "\t" + value + "\n");
		}
		
		if(usersVal[2].contains(user)){
			to_predict_val_files[2].append(user + "\t" + item + "\n");
			to_predict_true_val_files[2].append(user + "\t" + item + "\t" + value + "\n");
		}
		else{
			to_predict_test_files[2].append(user + "\t" + item + "\n");
			to_predict_true_test_files[2].append(user + "\t" + item + "\t" + value + "\n");
		}
		
		if(usersVal[3].contains(user)){
			to_predict_val_files[3].append(user + "\t" + item + "\n");
			to_predict_true_val_files[3].append(user + "\t" + item + "\t" + value + "\n");
		}
		else{
			to_predict_test_files[3].append(user + "\t" + item + "\n");
			to_predict_true_test_files[3].append(user + "\t" + item + "\t" + value + "\n");
		}
		
		if(usersVal[4].contains(user)){
			to_predict_val_files[4].append(user + "\t" + item + "\n");
			to_predict_true_val_files[4].append(user + "\t" + item + "\t" + value + "\n");
		}
		else{
			to_predict_test_files[4].append(user + "\t" + item + "\n");
			to_predict_true_test_files[4].append(user + "\t" + item + "\t" + value + "\n");
		}
		
	}
	
}






void writeRatedFiles(HashSet<String> rated, HashSet<String>[] usersVal, 
					 HashSet<String>[] usersTest, String HOME_DIR){
	
	//create the files for to_predict.fold$i$.val and to_predict.fold$i$.test
	String[] rated_val_filenames = new String[5];
	File[] rated_val_files = new File[5];
	
	String[] rated_test_filenames = new String[5];
	File[] rated_test_files = new File[5];
	
	println "About to create the to_predict files"
	
	for(int i=0;i<5;i++){
		rated_val_filenames[i] = "rated.fold" + (i+1) + ".val"
		rated_test_filenames[i] = "rated.fold" + (i+1) + ".test"
		rated_val_files[i] = createUsersFile(HOME_DIR + java.io.File.separator + rated_val_filenames[i])
		rated_test_files[i] = createUsersFile(HOME_DIR + java.io.File.separator + rated_test_filenames[i])
	}
	
	//println "print usersVal = " + usersVal;
	//println "print to_predict_true = " + to_predict_true;
	
	for(String entry : rated){
		
		//println "current entry to predict = " + entry
		
		String[] args = entry.split("\t")
		
		String user = args[0].toString();
		String item = args[1].toString();
		
		//println "user = " + user + " item = " + item;
		
		//if this user is not contained in the usersVal AND the usersTest then write it to both rated files
		if(!usersVal[0].contains(user) && !usersTest[0].contains(user)){
			rated_val_files[0].append(user + "\t" + item + "\n");
			rated_test_files[0].append(user + "\t" + item + "\n");
		}
		else{
			if(usersVal[0].contains(user)){
				rated_val_files[0].append(user + "\t" + item + "\n");
			}
			else{
				rated_test_files[0].append(user + "\t" + item + "\n");
			}
		}
		
		
		if(!usersVal[1].contains(user) && !usersTest[1].contains(user)){
			rated_val_files[1].append(user + "\t" + item + "\n");
			rated_test_files[1].append(user + "\t" + item + "\n");
		}
		else{
			if(usersVal[1].contains(user)){
				rated_val_files[1].append(user + "\t" + item + "\n");
			}
			else{
				rated_test_files[1].append(user + "\t" + item + "\n");
			}
		}
		
		
		if(!usersVal[2].contains(user) && !usersTest[2].contains(user)){
			rated_val_files[2].append(user + "\t" + item + "\n");
			rated_test_files[2].append(user + "\t" + item + "\n");
		}
		else{
			if(usersVal[2].contains(user)){
				rated_val_files[2].append(user + "\t" + item + "\n");
			}
			else{
				rated_test_files[2].append(user + "\t" + item + "\n");
			}
		}
		
		
		if(!usersVal[3].contains(user) && !usersTest[3].contains(user)){
			rated_val_files[3].append(user + "\t" + item + "\n");
			rated_test_files[3].append(user + "\t" + item + "\n");
		}
		else{
			if(usersVal[3].contains(user)){
				rated_val_files[3].append(user + "\t" + item + "\n");
			}
			else{
				rated_test_files[3].append(user + "\t" + item + "\n");
			}
		}
		
		if(!usersVal[4].contains(user) && !usersTest[4].contains(user)){
			rated_val_files[4].append(user + "\t" + item + "\n");
			rated_test_files[4].append(user + "\t" + item + "\n");
		}
		else{
			if(usersVal[4].contains(user)){
				rated_val_files[4].append(user + "\t" + item + "\n");
			}
			else{
				rated_test_files[4].append(user + "\t" + item + "\n");
			}
		}
		
	}
	
}



File createUsersFile(String fileName){
	println "About to create the file " + fileName;
	File usersFile = new File(fileName);
	if(!usersFile.createNewFile()){	//if the file already exists then delete it and create it
		usersFile.delete();
		usersFile.createNewFile();
	}
	return usersFile;
}


//this function is similar to the loadData with the difference that it reads the file and replace the ids with the uniqueIDs 
//that we have assigned when parsing the files with the unique IDs (users, artists, tags)
//instead of calling directly the PSL methods loadDelimitedDataTruth and loadDelimitedData
//we need to open the file, read it entry by entry, for each entry check if this id is contained in the 
//userIDsToNewUserIDs or itemIDsToNewItemIDs or tagIDsToNewTagIDs 
//if so, then we need to replace this witht he one that exists in the map and then insert it in the predicate
String loadDataUniqueIDs(String HOME_DIR, String userDir,
						HashMap<Integer, Integer> userIDsToNewUserIDs, 
						HashMap<Integer, Integer> itemIDsToNewItemIDs,
						//HashMap<Integer, Integer> tagIDsToNewTagIDs, 
						HashSet<String> users,
						HashSet<String> similarities_jaccard_items, HashSet<String> similarities_lastfm_items, 
						HashSet<String> similarities_tags_items,
						HashSet<String> similarities_cosine_users, HashSet<String> similarities_jaccard_users,
						HashSet<String> friends, HashSet<String> artists_tags,
						HashSet<String> to_predict, 
						HashSet<String> to_predict_true, 
						HashSet<String> rating_train, 
						HashSet<String> rated, 
						HashSet<String> rated_train,
						int TAGSTOKEEP,
						HashSet<String> candidateSet,
						int current_user_id, int VALID_SIZE,
						HashSet<String>[] usersVal,
						HashSet<String>[] usersTest){
		
		
		
		
		HashSet<String> rated_train_items_only = new HashSet();	//this is helpful structure and keeps from the pairs 
																//of the structure rated_train only the items (i.e., not the users)
		
						
		HashSet<String> current_rating_15 = new HashSet();
		//this call returns the id of the user
		String userID = loadToOneStructure(userDir + "rated.train", userIDsToNewUserIDs, itemIDsToNewItemIDs, current_rating_15)
		
		if(current_rating_15.size()>15){
			println "Current size bigger than 15 ratings.. About to skip this user"
			return null;
		}
		
		//the size of the current_rating_15 should be 15 if not then I need to skip this user
		if(current_rating_15.size()<15){
			println "Did not find 15 ratings for this user... About to skip him"
			return null;
		}
		
		//println "Insert user " + userID 
		users.add(userID); //insert this user to the structure
		
		//the first 15 entries of the current_rating_15 will be inserted to the structures rating_train
		//the last 5 entries will be inserted into the structure to_predict and to_predict_true
		//ALL the entries will be inserted in the structure rated
		//iterate over the current_rating_20 and implement the above
		int counter = 1;
		for(String entry : current_rating_15){
			String item = entry.split("\t")[1];
			//if(counter<=15){
			rating_train.add(entry);
			rated_train.add(entry);
			rated_train_items_only.add(item);
//			}
//			else{	
//				to_predict.add(entry);
//				to_predict_true.add(entry+"\t1");
//				candidateSet.add(item);
//			}
			rated.add(entry);
			//counter++;
		}
		
		//println "Size of rated_train_items_only = " + rated_train_items_only.size();
		//println "User = " + userID + " " + rated_train_items_only;
		//println "Size of candidateSet after the first insertion for user " + userID + " is " +  candidateSet.size();
		
		
		//load the entries of the file inputuser_artist.topredict
		HashSet<String> current_to_predict = new HashSet();
		if(loadToOneStructure(userDir + "inputuser_artist.topredict", userIDsToNewUserIDs, itemIDsToNewItemIDs, current_to_predict)==null){
			print "[ERROR]: Did not find file.. Skipping this directory..."
			return null;
		}
		//println "For user " + userID + " Size of current_to_predict = " + current_to_predict.size();
		//put the entries of the structure current_to_predict to the to_predict, to_predict_true (with zero value), and rated
		for(String entry : current_to_predict){
			//insert it only if it is not contained already in the rating_train and to_predict structures
			if(!rating_train.contains(entry) && !to_predict.contains(entry)){
				to_predict.add(entry);
				to_predict_true.add(entry+"\t0");
				rated.add(entry);
				String item = entry.split("\t")[1]; //get the item only 
				candidateSet.add(item);
			}
			else if(rating_train.contains(entry)){
				//println "user and item = " + entry + " exists in the rating_train."
			}
			else if(to_predict.contains(entry)){
				//println "user and item = " + entry + " exists in the to_predict."
			}
		}
		
		//println "Size of candidateSet after the second insertion for user " + userID + " is " +  candidateSet.size();
		
		//now load the training data that will be loaded in the rating_train structure
		HashSet<String> current_rating_train = new HashSet();
		if(loadToOneStructure(userDir + "user_artist.graph.binary", userIDsToNewUserIDs, itemIDsToNewItemIDs, current_rating_train)==null){
			print "[ERROR]: Did not find file.. Skipping this directory..."
			return null;
		}
		//println "Size of the structure current_rating_train = " + current_rating_train.size();
		//the entries of the structure current_rating_train will be inserted in the rating_train (if not already exist there)
		//and in the rated structure
		for(String entry : current_rating_train){
			if(!rating_train.contains(entry) && !to_predict.contains(entry)){	//the second part is in order not to put the rating that I want to predict in the rating_train
				rating_train.add(entry);
			}
			if(!rated.contains(entry)){
				rated.add(entry);
			}
		}
		
		
		//load the below similarities ONLY IF the artist is contained in the training set, i.e. in the structure rated_train
		
		//now load the similarity files
		//println "Size of structure similarities_jaccard_items before the insertion = " + similarities_jaccard_items.size()
		loadToArtistSimilaritiesStructure(userDir + "artist-artist.user.jaccard.top20.ones", itemIDsToNewItemIDs, similarities_jaccard_items, rated_train_items_only)
		//println "Size of structure similarities_jaccard_items after the insertion = " + similarities_jaccard_items.size()
		
		//similarities: item-item last.fm
		//println "Size of structure similarities_lastfm_items before the insertion = " + similarities_lastfm_items.size()
		loadToArtistSimilaritiesStructure(userDir + "artist-artist.lastfm.top20.ones", itemIDsToNewItemIDs, similarities_lastfm_items, rated_train_items_only)
		//println "Size of structure similarities_lastfm_items after the insertion = " + similarities_lastfm_items.size()

		//similarities: item-item tag
		//println "Size of structure sim_tags_items before the insertion = " + similarities_tags_items.size()
		loadToArtistSimilaritiesStructure(userDir + "artist-artist.tags.jaccard.top20.ones", itemIDsToNewItemIDs, similarities_tags_items, rated_train_items_only)
		//println "Size of structure sim_tags_items after the insertion = " + similarities_tags_items.size()
		
		//similarities: user-user cosine
		//println "Size of structure similarities_cosine_users before the insertion = " + similarities_cosine_users.size()
		loadToSimilaritiesStructure(userDir + "inputuser-user.artist.cosine.top20.ones", userIDsToNewUserIDs, similarities_cosine_users)
		//println "Size of structure similarities_cosine_users after the insertion = " + similarities_cosine_users.size()

		//similarities: user-user jaccard
		//println "Size of structure similarities_jaccard_users before the insertion = " + similarities_jaccard_users.size()
		loadToSimilaritiesStructure(userDir + "inputuser-user.artist.jaccard.top20.ones", userIDsToNewUserIDs, similarities_jaccard_users)
		//println "Size of structure similarities_jaccard_users after the insertion = " + similarities_jaccard_users.size()
		
		//println "Size of structure friends before the insertion = " + friends.size()
		loadToSimilaritiesStructure(userDir + "inputuser_friend.graph", userIDsToNewUserIDs, friends)
		//println "Size of structure friends after the insertion = " + friends.size()

		//tags
//		println "Size of structure artists_tags before the insertion = " + artists_tags.size()
//		loadToTwoStructures(userDir + "artist-tags", itemIDsToNewItemIDs, tagIDsToNewTagIDs, artists_tags)
//		println "Size of structure artists_tags after the insertion = " + artists_tags.size()

		//println "Size of structure artists_tags before the insertion = " + artists_tags.size()
		//loadToTwoStructures(userDir + "artist-tags", itemIDsToNewItemIDs, tagIDsToNewTagIDs, artists_tags)
		loadTags(userDir + "artist-tags", itemIDsToNewItemIDs, artists_tags, TAGSTOKEEP)
		//println "Size of structure artists_tags after the insertion = " + artists_tags.size()

		
		//create a file with only one entry: the id of this user
		HashSet<String> singleUserID = new HashSet();
		singleUserID.add(userID)
		
		//make sure you do not include more than 5000 users
		if(current_user_id>=0 && current_user_id<5*VALID_SIZE){
			//split into validation and test
			//fold 1
			if(current_user_id >=0 && current_user_id<VALID_SIZE){
				//at this point write the content of the candidateSet to a file named after candidateSet.userID
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold1.val." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold1.val." + userID, singleUserID, false, 0);
				usersVal[0].add(userID);
			}
			else{
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold1.test." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold1.test." + userID, singleUserID, false, 0);
				usersTest[0].add(userID);
			}
			//fold 2
			if(current_user_id >=VALID_SIZE && current_user_id<2*VALID_SIZE){
				//at this point write the content of the candidateSet to a file named after candidateSet.userID
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold2.val." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold2.val." + userID, singleUserID, false, 0);
				usersVal[1].add(userID);
			}
			else{
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold2.test." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold2.test." + userID, singleUserID, false, 0);
				usersTest[1].add(userID);
			}
			//fold 3
			if(current_user_id >=2*VALID_SIZE && current_user_id<3*VALID_SIZE){
				//at this point write the content of the candidateSet to a file named after candidateSet.userID
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold3.val." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold3.val." + userID, singleUserID, false, 0);
				usersVal[2].add(userID);
			}
			else{
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold3.test." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold3.test." + userID, singleUserID, false, 0);
				usersTest[2].add(userID);
			}
			//fold 4
			if(current_user_id >=3*VALID_SIZE && current_user_id<4*VALID_SIZE){
				//at this point write the content of the candidateSet to a file named after candidateSet.userID
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold4.val." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold4.val." + userID, singleUserID, false, 0);
				usersVal[3].add(userID);
			}
			else{
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold4.test." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold4.test." + userID, singleUserID, false, 0);
				usersTest[3].add(userID);
			}
			//fold 5
			if(current_user_id >=4*VALID_SIZE && current_user_id<5*VALID_SIZE){
				//at this point write the content of the candidateSet to a file named after candidateSet.userID
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold5.val." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold5.val." + userID, singleUserID, false, 0);
				usersVal[4].add(userID);
			}
			else{
				writeToFile(HOME_DIR + java.io.File.separator + "candidateSet.fold5.test." + userID, candidateSet, false, 0);
				writeToFile(HOME_DIR + java.io.File.separator + "user.fold5.test." + userID, singleUserID, false, 0);
				usersTest[4].add(userID);
			}
		}

		//create a file with only one entry: the id of this user
		//HashSet<String> singleUserID = new HashSet();
		//singleUserID.add(userID)
		
		return "";
}
	

							
							
//load PSL data to the respective predicates
void loadDelimitedData(DataStore data, def predicate, Partition partition, HashSet<String> set){
	
	println "About to insert data to the predicate " + predicate;
	
	Inserter inserter = data.getInserter(predicate, partition);
	
	//for each entry in the set
	for(String entry : set){
		Integer id1 = Integer.parseInt(entry.split("\t")[0]);
		Integer id2 = Integer.parseInt(entry.split("\t")[1]);
		
		inserter.insert(id1,id2);
	}
	
}

						
//each file has 2 or 3 fiels: id1 id2 (value)
//the hashset with the similarities is of the form "id1\tid2"
void loadToSimilaritiesStructure(String fileName, HashMap<Integer, Integer> IDsToNewIDs, HashSet<String> similarities){

	//read the file line by line
	def ids = new File(fileName)
	
	//check if the file exists or not
	if(!ids.exists()){
		println "ERROR: The file " + fileName + " does not exist!"
		return null;
	}
	
	String tag
	String[] tokens
	ids.eachLine {
		line ->
		tokens = line.split("\t")
		if(tokens.length==2 || tokens.length==3){
			Integer id1 = Integer.parseInt(tokens[0].toString());
			Integer id2 = Integer.parseInt(tokens[1].toString());
			//int value = Integer.parseInt(tokens[2].toString());
			
			//check if id1 or id2 exist in the structure 
			//if so then replace those with the real id 
			if(IDsToNewIDs.containsKey(id1)){
				id1 = IDsToNewIDs.get(id1)
			}
			if(IDsToNewIDs.containsKey(id2)){
				id2 = IDsToNewIDs.get(id2)
			}
			String entry = id1.toString() + "\t" + id2.toString();
			//insert the entry in the set if it is contained already the results will be the same
			similarities.add(entry);
			
		}
		else{
			println "[ERROR]:loadDelimitedDataTruth: The length of the structure tokens is not 2 or 3."
			return;
		}
	}
}
				

//same as the above with the following difference: load the similarity ONLY IF one of the two artists is contained in the
//training data, i.e., rated_train_items_only	
void loadToArtistSimilaritiesStructure(String fileName, HashMap<Integer, Integer> IDsToNewIDs, HashSet<String> similarities,
									   HashSet<String> rated_train_items_only){
	
		//read the file line by line
		def ids = new File(fileName)
		
		//check if the file exists or not
		if(!ids.exists()){
			println "ERROR: The file " + fileName + " does not exist!"
			return null;
		}
		
		String tag
		String[] tokens
		ids.eachLine {
			line ->
			tokens = line.split("\t")
			if(tokens.length==2 || tokens.length==3){
				Integer id1 = Integer.parseInt(tokens[0].toString());
				Integer id2 = Integer.parseInt(tokens[1].toString());
				//int value = Integer.parseInt(tokens[2].toString());
				
				//check if id1 or id2 exist in the structure
				//if so then replace those with the real id
				if(IDsToNewIDs.containsKey(id1)){
					id1 = IDsToNewIDs.get(id1)
				}
				if(IDsToNewIDs.containsKey(id2)){
					id2 = IDsToNewIDs.get(id2)
				}
				String entry = id1.toString() + "\t" + id2.toString();

				//insert the entry if it is contained in the rated_train_items_only structure only 
				if(rated_train_items_only.contains(id1.toString()) || rated_train_items_only.contains(id2.toString()))
					similarities.add(entry);
				
			}
			else{
				println "[ERROR]:loadDelimitedDataTruth: The length of the structure tokens is not 2 or 3."
				return;
			}
		}
	}
	
//called with users-items
String loadToTwoStructures(String fileName, HashMap<Integer, Integer> userIDsToNewUserIDs,
					    HashMap<Integer, Integer> itemIDsToNewitemIDs, HashSet<String> users_items,
						HashSet<String> current_users_items, boolean toPrint){
	//read the file line by line
	def ids = new File(fileName)
	//check if the file exists or not
	if(!ids.exists()){
		println "ERROR: The file " + fileName + " does not exist!"
		return null;
	}
	
	String tag
	Integer id1, id2;
	String[] tokens
	ids.eachLine {
		line ->
		tokens = line.split("\t")
		if(tokens.length==2 || tokens.length==3){
			id1 = Integer.parseInt(tokens[0].toString());
			id2 = Integer.parseInt(tokens[1].toString());
			//int value = Integer.parseInt(tokens[2].toString());
			
			//check if id1 or id2 exist in the structure
			//if so then replace those with the real id
			if(userIDsToNewUserIDs.containsKey(id1)){
				id1 = userIDsToNewUserIDs.get(id1)
			}
			if(itemIDsToNewitemIDs.containsKey(id2)){
				id2 = itemIDsToNewitemIDs.get(id2)
			}
			String entry = id1.toString() + "\t" + id2.toString();
			//insert the entry in the set if it is contained already the results will be the same
			if(toPrint){
				if(users_items.contains(entry))
					println "Entry " +  entry + " already exists in users_items." 
				else{
					println "About to insert entry " +  entry;
				}
			}
			users_items.add(entry)
			current_users_items.add(entry);
		}
		else{
			println "[ERROR]:loadDelimitedDataTruth: The length of the structure tokens is not 2 or 3."
			return null;
		}
		return id1;
	}
}
	

//called with users-items
String getUserID(String fileName, HashMap<Integer, Integer> userIDsToNewUserIDs){
	//read the file line by line
	def ids = new File(fileName)
	//check if the file exists or not
	if(!ids.exists()){
		println "ERROR: The file " + fileName + " does not exist!"
		return null;
	}
	
	Integer id1;
	String[] tokens
	ids.eachLine {
		line ->
		tokens = line.split("\t")
		if(tokens.length==2 || tokens.length==3){
			id1 = Integer.parseInt(tokens[0].toString());
			if(userIDsToNewUserIDs.containsKey(id1)){
				id1 = userIDsToNewUserIDs.get(id1)
			}
			//println "Inside getUserID -- id = " + id1
			return id1;
		}
		else{
			println "[ERROR]:loadDelimitedDataTruth: The length of the structure tokens is not 2 or 3."
			return null;
		}
	}
}
						
						
						
//called with users-items
String loadToOneStructure(String fileName, HashMap<Integer, Integer> userIDsToNewUserIDs,
						HashMap<Integer, Integer> itemIDsToNewitemIDs, HashSet<String> users_items){
	//read the file line by line
	def ids = new File(fileName)
	//check if the file exists or not
	if(!ids.exists()){
		println "ERROR: The file " + fileName + " does not exist!"
		return null;
	}
	
	String tag
	Integer id1, id2;
	String[] tokens
	ids.eachLine {
		line ->
		tokens = line.split("\t")
		if(tokens.length==2 || tokens.length==3){
			id1 = Integer.parseInt(tokens[0].toString());
			id2 = Integer.parseInt(tokens[1].toString());
			//int value = Integer.parseInt(tokens[2].toString());
			
			//check if id1 or id2 exist in the structure
			//if so then replace those with the real id
			if(userIDsToNewUserIDs.containsKey(id1)){
				//print "User " + id1 + " exists in the structure userIDsToNewUserIDs with id "
				id1 = userIDsToNewUserIDs.get(id1)
				//println id1;
			}
			if(itemIDsToNewitemIDs.containsKey(id2)){
				//print "Item " + id2 + " exists in the structure itemIDsToNewitemIDs with id "
				id2 = itemIDsToNewitemIDs.get(id2)
				//println id2;
			}
			String entry = id1.toString() + "\t" + id2.toString();
			//insert the entry in the set 
			if(users_items.contains(entry)){
				//println "Entry " + entry + " is contained in the structure users_items"
			}
			users_items.add(entry);
		}
		else{
			println "[ERROR]:loadDelimitedDataTruth: The length of the structure tokens is not 2 or 3."
			return null;
		}
		return id1;
	}
}
						
						
//we keep only the ids which have id between 1 and 1000 (i.e. we keep only the ids that belong to the 1,000 most popular
String loadTags(String fileName, HashMap<Integer, Integer> itemIDsToNewItemIDs,
				HashSet<String> artists_tags, int TAGSTOKEEP){
	//read the file line by line
	def ids = new File(fileName)
	String tag
	String[] tokens
	ids.eachLine {
		line ->
		tokens = line.split("\t")
		if(tokens.length==2 || tokens.length==3){
			Integer id1 = Integer.parseInt(tokens[0].toString());
			Integer id2 = Integer.parseInt(tokens[1].toString());
			//int value = Integer.parseInt(tokens[2].toString());
			
			//check if id1 or id2 exist in the structure
			//if so then replace those with the real id
			if(itemIDsToNewItemIDs.containsKey(id1)){
				id1 = itemIDsToNewItemIDs.get(id1)
			}
			if(id2<=TAGSTOKEEP){ //insert it only if the id2 is between 1 and 1000
				String entry = id1.toString() + "\t" + id2.toString();
				//insert the entry in the set if it is contained already the results will be the same
				artists_tags.add(entry);
				return id1;
			}
		}
		else{
			println "[ERROR]:loadDelimitedDataTruth: The length of the structure tokens is not 2 or 3."
			return null;
		}
	}
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
	
	 							
    
//function that reads the files with the similarities
void loadSimilarities(HashMap<String, Double> item_item_similarities, String fileName){
	
	println "About to load the similarities file " + fileName
	
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

//loads a file where each line is the md5 of a user
HashSet<String> loadUserMD5s(String fileName, String HOME_DIR){
	HashSet<String> md5s = new HashSet();
	
	def md5_users = new File(HOME_DIR + java.io.File.separator + fileName)
	md5_users.eachLine {
		line ->
		md5s.add(line)
	}
	
	return md5s;
}
    							
//function that reads the structures userID_name, businessID_name, and tagID_name
//the structure initialArtistNameID stores the name and the initial unique id that are in the file artistUniqueIDs
//this structure will be used by the function loadToPredictTrue later in the code
HashMap<Integer, Integer> loadData(String fileName, HashMap<String, String> IDsToNames, HashMap<String, String> namesToIDs,
								  HashMap<String, Integer> initialNameID){
	
	HashMap<Integer, Integer> IDsToNewIDs = new HashMap();
	//initialNameID = new HashMap();
	def ids_names = new File(fileName)
	
	//check if the file exists or not
	if(!ids_names.exists()){
		println "ERROR: The file " + fileName + " does not exist!"
		return null;
	}

	
	def words, uniqueID, name;
	Integer firstUniqueID;
	ids_names.eachLine {
		line ->
		words = line.split("\t")
		name=words[0].toString().replace("(", "").replace(")","").replace("\"","").replace(".","").replace(",","");
		uniqueID = words[1].toString();
		firstUniqueID = Integer.parseInt(uniqueID);
		
		initialNameID.put(name,firstUniqueID);
		
		//println "Name = " + name + " uniqueID = " + uniqueID + " firstUniqueID = " + firstUniqueID
		
		//if the name is contained in the structure namesToIDs but with a different ID 
		//then I need to add the entry in the IDsToNewIDs structure
		//but i do not need to update the structures IDsToNames and namesToIDs
		if(namesToIDs.containsKey(name) && !namesToIDs.get(name).equals(uniqueID)){
			//in this case firstUniqueID=uniqueID
			//println "Name " + name + " exists in the structure namesToIDs but with id " + namesToIDs.get(name) + " instead of " + uniqueID
			//update the IDsToNewIDs structure
			if(!IDsToNewIDs.containsKey(firstUniqueID)){
				//println "IDsToNewIDs: Insert entry (" + firstUniqueID + "," + Integer.parseInt(namesToIDs.get(name)) + ")";
				IDsToNewIDs.put(firstUniqueID, Integer.parseInt(namesToIDs.get(name)))
			}
			else{
				println "BUG? firstUniqueID = " + firstUniqueID + " already exists in IDsToNewIDs";
			}
		}
		//if the name is not contained in the structure namesToIDs but the id exists in the structure  IDsToNames
		//then I need to insert the entry in the structures IDsToNames and NamesToIDs
		if(!namesToIDs.containsKey(name) && IDsToNames.containsKey(uniqueID)){
			//print "Replace id " + uniqueID + " with name " + name + " with id ";
			//println "Name " + name + " does not exist in the structure namesToIDs but id " + uniqueID + " exists in the structure IDsToNames"
			//find a new id
			while(IDsToNames.containsKey(uniqueID)){	//in case it is contained in the structure then generate a new integer for this entry
				uniqueID = (Integer.parseInt(uniqueID)+1).toString()
			}
			//println uniqueID;
			//here we should insert an entry to the structure IDsToNewIDs
			//println "2. Insert entry (" + name + "," + uniqueID + ")";
			//println "IDsToNewIDs: Insert entry (" + firstUniqueID + "," + Integer.parseInt(uniqueID) + ")";
			IDsToNewIDs.put(firstUniqueID, Integer.parseInt(uniqueID))
			//and insert the new artist in the structures IDsToNames and namesToIDs
			IDsToNames.put(uniqueID,name);	//update the structure uniqueID -> name
			namesToIDs.put(name, uniqueID);	//update the structure name -> uniqueID
		}
		//this is the simplest case where NEITHER the id exists in the structure IDsToNames
		//nor the name exists in the structure NamesToIDs 
		//the only thing we need to do is to insert this entry in both structures (no need to insert entry in the structure IDsToNewIDs)
		if(!namesToIDs.containsKey(name) && !IDsToNames.containsKey(uniqueID)){
			//println "1. Insert entry (" + name + "," + uniqueID + ")";
			IDsToNames.put(uniqueID,name);	//update the structure uniqueID -> name
			namesToIDs.put(name, uniqueID);	//update the structure name -> uniqueID
		}
		
		
	}
	//println "Size of structure IDsToNames = " +  IDsToNames.size();
	//println "Size of structure namesToIDs = " +  namesToIDs.size();
	//println "Size of structure initialNameID = " +  initialNameID.size();
	
	return IDsToNewIDs;
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
    


    public static void main(String[] args) {
        PreprocessOfflineEvaluation5Folds preprocess = new PreprocessOfflineEvaluation5Folds() ;
        preprocess.doit(args[0], args[1], args[2]);
        // done
    }
}
