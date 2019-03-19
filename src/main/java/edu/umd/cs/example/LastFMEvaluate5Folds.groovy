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





public class LastFMEvaluate5Folds {


public doit(String HOME_DIR) {

	
	double recallAt5=0, recallAt10=0, recallAt25=0, recallAt50=0;
	
	for(int fold=1;fold<=5;fold++){
	
		
		int sim_jaccard_artists_weight, sim_lastfm_weight, sim_tag_artists_weight, sim_cosine_users_weight;
		int sim_jaccard_users_weight, friends_weight, prior, item_popular_weight;
		
		//for now same weights for all folds
		//if(fold==1){
			//these are the best
//			sim_jaccard_artists_weight = 50
//			sim_lastfm_weight = 50
//			sim_tag_artists_weight = 10
//			sim_cosine_users_weight = 10
//			sim_jaccard_users_weight = 10
//			friends_weight = 50
//			prior = 50
//			item_popular_weight = 1
			
			//this is just a test
			sim_jaccard_artists_weight = 1
			sim_lastfm_weight = 50
			sim_tag_artists_weight = 1
			sim_cosine_users_weight = 1
			sim_jaccard_users_weight = 1
			friends_weight = 1
			prior = 100
			item_popular_weight = 2
		
//		}
//		if(fold==2){
//			sim_jaccard_artists_weight = 1
//			sim_lastfm_weight = 1
//			sim_tag_artists_weight = 1
//			sim_cosine_users_weight = 1
//			sim_jaccard_users_weight = 1
//			friends_weight = 1
//			prior = 1
//			item_popular_weight = 1
//		}
//		if(fold==3){
//			sim_jaccard_artists_weight = 1
//			sim_lastfm_weight = 1
//			sim_tag_artists_weight = 1
//			sim_cosine_users_weight = 1
//			sim_jaccard_users_weight = 1
//			friends_weight = 1
//			prior = 1
//			item_popular_weight = 1
//		}
//		if(fold==4){
//			sim_jaccard_artists_weight = 1
//			sim_lastfm_weight = 1
//			sim_tag_artists_weight = 1
//			sim_cosine_users_weight = 1
//			sim_jaccard_users_weight = 1
//			friends_weight = 1
//			prior = 1
//			item_popular_weight = 1
//		}
//		if(fold==5){
//			sim_jaccard_artists_weight = 1
//			sim_lastfm_weight = 1
//			sim_tag_artists_weight = 1
//			sim_cosine_users_weight = 1
//			sim_jaccard_users_weight = 1
//			friends_weight = 1
//			prior = 1
//			item_popular_weight = 1
//		}
		
		int i_db=0;
		
			
	    //keep track of the time
	    println "START Time for fold  " + fold + " is " + new Date();
		
		String db_name = "db" + i_db + ".fold." + fold;
		
		//about to create the database for this search
		createDataBase(db_name, HOME_DIR)
		
		println "Run for for prior = " + prior + 
		 " sim_jaccard_artists_weight = " + sim_jaccard_artists_weight +
		 " sim_lastfm_weight = " + sim_lastfm_weight +
		 " sim_tag_artists_weight = " + sim_tag_artists_weight +
		 " sim_cosine_users_weight = " + sim_cosine_users_weight + 
		 " sim_jaccard_users_weight = " + sim_jaccard_users_weight +
		 " item_popular_weight = " + item_popular_weight + 
		 " friends_weight = " + friends_weight;
		
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
	 	
		Integer LIMIT = 50;	//the number of items to keep for each user 
	    DataStore data = new RDBMSDataStore(new PostgreSQLDriver(db_name, true), config);
	    //DataStore data = new RDBMSDataStore(new PostgreSQLDriver(user_md5_1, true), config);
	    		
		int partitionID = 0;	//this id is for creating partitions with unique ids each time
		
		def userDir = HOME_DIR + java.io.File.separator ;//+ "integratedMyMediaLite" + java.io.File.separator;
		
		String ratedTestTrueFile = "to_predict_true.fold" + fold + ".test";		//these are the truth data for the evaluation 
						
		//create the PSL model
		PSLModel m = createModel(this, data, prior, sim_jaccard_artists_weight, sim_lastfm_weight, sim_tag_artists_weight,
								sim_cosine_users_weight, sim_jaccard_users_weight, friends_weight, item_popular_weight);
		
		println m;
	
		//we put in the same partition things that are observed
		Partition evidencePartitionCommon = data.getPartition((partitionID++).toString());
		Partition evidencePartition = data.getPartition((partitionID++).toString());
		Partition targetPartition = data.getPartition((partitionID++).toString());
	
		Partition evidencePartitionWL = data.getPartition((partitionID++).toString());// observed data for weight learning
		Partition targetPartitionWL = data.getPartition((partitionID++).toString());// unobserved data for weight learning
		Partition truePartitionWL = data.getPartition((partitionID++).toString());  // train set for inference
	
		//load the evidence partition common data
		loadEvidenceData(evidencePartitionCommon, data, userDir, fold, "test")
			
		println "About to load the data for the inference..."
		
		//load data for inference
		loadEvidenceData(evidencePartition, data, userDir, "rated_train", "rated.fold" + fold + ".test")	//the first argument loads the data for the ratedTrain predicate (rated_train file) and the second argument loads data for the rated predicate (rated file)
		
		println "About to load the target partition data"
		loadTargetData(targetPartition, data, userDir, "to_predict.fold" + fold + ".test")
		
		println "About to perform inference "
		
		//performs inference and creates the structure predicted_user_rating_item
		performInference(m, data, config, evidencePartitionCommon, evidencePartition, targetPartition,
						predicted_user_rating_item, item_grounding, LIMIT);
		
		HashMap<String, HashSet<String>> true_user_rating_item = new HashMap();	//each entry is of the form <user, <item1,item2,...,itemN>>
					
		loadTrueUserRatingItems(userDir+ratedTestTrueFile, true_user_rating_item);
		
		//now evaluate the results	
		int k = 5;
		double current_recall = computeRecallAtTopK(k, true_user_rating_item, predicted_user_rating_item, prior, sim_jaccard_artists_weight, sim_lastfm_weight, sim_tag_artists_weight, sim_cosine_users_weight, sim_jaccard_users_weight, item_popular_weight, friends_weight)
		recallAt5 += current_recall
		print "Fold " + fold + " Stats for prior = " + prior +
		" sim_jaccard_artists_weight = " + sim_jaccard_artists_weight +
		" sim_lastfm_weight = " + sim_lastfm_weight +
		" sim_tag_artists_weight = " + sim_tag_artists_weight +
		" sim_cosine_users_weight = " + sim_cosine_users_weight +
		" sim_jaccard_users_weight = " + sim_jaccard_users_weight +
		" item_popular_weight = " + item_popular_weight +
		" friends_weight = " + friends_weight + " ";
		println "recall@" + k + "=" + current_recall;
		
		k = 10;
		current_recall = computeRecallAtTopK(k, true_user_rating_item, predicted_user_rating_item, prior, sim_jaccard_artists_weight, sim_lastfm_weight, sim_tag_artists_weight, sim_cosine_users_weight, sim_jaccard_users_weight, item_popular_weight, friends_weight)
		recallAt10 += current_recall
		print "Fold " + fold + " Stats for prior = " + prior +
		" sim_jaccard_artists_weight = " + sim_jaccard_artists_weight +
		" sim_lastfm_weight = " + sim_lastfm_weight +
		" sim_tag_artists_weight = " + sim_tag_artists_weight +
		" sim_cosine_users_weight = " + sim_cosine_users_weight +
		" sim_jaccard_users_weight = " + sim_jaccard_users_weight +
		" item_popular_weight = " + item_popular_weight +
		" friends_weight = " + friends_weight + " ";
		println "recall@" + k + "=" + current_recall;
		
		k = 25;
		current_recall = computeRecallAtTopK(k, true_user_rating_item, predicted_user_rating_item, prior, sim_jaccard_artists_weight, sim_lastfm_weight, sim_tag_artists_weight, sim_cosine_users_weight, sim_jaccard_users_weight, item_popular_weight, friends_weight)
		recallAt25 += current_recall
		print "Fold " + fold + " Stats for prior = " + prior +
		" sim_jaccard_artists_weight = " + sim_jaccard_artists_weight +
		" sim_lastfm_weight = " + sim_lastfm_weight +
		" sim_tag_artists_weight = " + sim_tag_artists_weight +
		" sim_cosine_users_weight = " + sim_cosine_users_weight +
		" sim_jaccard_users_weight = " + sim_jaccard_users_weight +
		" item_popular_weight = " + item_popular_weight +
		" friends_weight = " + friends_weight + " ";
		println "recall@" + k + "=" + current_recall;
		
		k = 50;
		current_recall = computeRecallAtTopK(k, true_user_rating_item, predicted_user_rating_item, prior, sim_jaccard_artists_weight, sim_lastfm_weight, sim_tag_artists_weight, sim_cosine_users_weight, sim_jaccard_users_weight, item_popular_weight, friends_weight)
		recallAt50 += current_recall
		print "Fold " + fold + " Stats for prior = " + prior +
		" sim_jaccard_artists_weight = " + sim_jaccard_artists_weight +
		" sim_lastfm_weight = " + sim_lastfm_weight +
		" sim_tag_artists_weight = " + sim_tag_artists_weight +
		" sim_cosine_users_weight = " + sim_cosine_users_weight +
		" sim_jaccard_users_weight = " + sim_jaccard_users_weight +
		" item_popular_weight = " + item_popular_weight +
		" friends_weight = " + friends_weight + " ";
		println "recall@" + k + "=" + current_recall;
		
	    //delete the DB
	    println "END Time is " + new Date();    
		
		//dropdb that i created 
		deleteDataBase(db_name, HOME_DIR)
		
		i_db++;
	}//end of folds
	
	println "Final stats:"
	println "RecallAt5 = " + recallAt5/5.0 ;
	println "RecallAt10 = " + recallAt10/5.0 ;
	println "RecallAt25 = " + recallAt25/5.0 ;
	println "RecallAt50 = " + recallAt50/5.0 ;

}//end of script


//delete the database
void createDataBase(String db_name, String HOME_DIR){
	//println "About to delete the database..."
	try {
		String CMD = "createdb " + db_name ;
		Process p = Runtime.getRuntime().exec(CMD, null, new File(HOME_DIR)); p.waitFor();
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



//delete the database
void deleteDataBase(String db_name, String HOME_DIR){
	//println "About to delete the database..."
	try {
		String CMD = "dropdb " + db_name ;
		Process p = Runtime.getRuntime().exec(CMD, null, new File(HOME_DIR)); p.waitFor();
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




	
double computeRecallAtTopK(int k, HashMap<String, HashSet<String>> true_user_rating_item, 
	HashMap<String,TreeMultimap<Double, String>> predicted_user_rating_item,
	int prior, int sim_jaccard_artists_weight, int sim_lastfm_weight, 
	int sim_tag_artists_weight, int sim_cosine_users_weight, int sim_jaccard_users_weight,
	double item_popular_weight,
	int friends_weight){
	
	int count_yes=0;
	//iterate over the users
	for(String user : predicted_user_rating_item.keySet()){
		int count_yes_per_user=0;
		println "User: " + user
		println "True items are : " + true_user_rating_item.get(user);
		
		HashSet<String> best_items = new HashSet(); //the size of this structure should be K
		//for this user create a structure which contains only the top K items
		if(predicted_user_rating_item.containsKey(user)){
			TreeMultimap<Double, String> rating_item = predicted_user_rating_item.get(user);
			Set<Double> ratings = rating_item.keySet();
			for(Double rating : ratings){
				Set<String> items = rating_item.get(rating);
				for(String item : items){
					if(best_items.size()<k){
						//println "Insert in best_item: item " + item + " with value " + rating //+ " with the groundings "
						best_items.add(item);
					}
					else{
						break;
					}
				}
			}
			//now that i create the structure which keeps the items with the highest score, it is time to evaluate how well we did
			//println "Size of the structure best_items = " + best_items.size();
			//at the end, the structure best_items will contain the items with the highest scores
			for(String true_item : true_user_rating_item.get(user)){
				if(best_items.contains(true_item)){
					count_yes ++;
					count_yes_per_user++;
					//println "we got item " + true_item + " !"
				}
			}
			println "For user = " + user + " we got " + count_yes_per_user + " right, in top " + k;
			//println "For up to user = " + user + " we got " + count_yes + " right, in top " + k;
		}
		else{
			println "ERROR: Could not find user " + user + " in the structure predicted_user_rating_item"
		}
		
	}
	//here compute the stats
	println "We got " + count_yes + " items right."
	println "Size of the structure true_user_rating_item = " + computeSizeOfStructure(true_user_rating_item);
	Double recallAtK = 1.0*count_yes / computeSizeOfStructure(true_user_rating_item);
	print "Stats for prior = " + prior + 
	" sim_jaccard_artists_weight = " + sim_jaccard_artists_weight + 
	" sim_lastfm_weight = " + sim_lastfm_weight +
	" sim_tag_artists_weight = " + sim_tag_artists_weight +
	" sim_cosine_users_weight = " + sim_cosine_users_weight + 
	" sim_jaccard_users_weight = " + sim_jaccard_users_weight +
	" item_popular_weight = " + item_popular_weight + 
	" friends_weight = " + friends_weight + " ";
	println "recall@" + k + "=" + recallAtK;
	
	return recallAtK;
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
	
void loadTrueUserRatingItems(String fileName, HashMap<String, HashSet<String>>  true_user_rating_item){
	
	println "About to store the ground truth " + fileName
	
	def users_items = new File(fileName)
	
	//check if the file exists or not
	if(!users_items.exists()){
		println "ERROR: The file " + fileName + " does not exist!"
		return null;
	}
	
	def words
	users_items.eachLine{
		line ->
		words = line.split("\t")
		String user = words[0].toString();
		String item = words[1].toString();
		Double value = Double.parseDouble(words[2].toString())
		
		//store the entry in the structure only if the value is 1
		if(value==1){
			if(!true_user_rating_item.containsKey(user)){	//first time i encounter this user	
				HashSet<String> items = new HashSet();
				items.add(item)
				true_user_rating_item.put(user, items)
			}
			else{	//not the first time for this  user
				HashSet<String> items = true_user_rating_item.get(user)
				items.add(item)
			}
		} 
	}

	println "Done with parsing the structure with the ground truth - size = " + true_user_rating_item.size();
	
	//println true_user_rating_item;
}
	
	
void performInference(PSLModel m, DataStore data, ConfigBundle config, Partition evidencePartitionCommon, 
	Partition evidencePartition, Partition targetPartition, 
	HashMap<String,TreeMultimap<Double, String>> predicted_user_rating_item, 
	HashMap<String, HashSet<String>> item_grounding, Integer LIMIT){
	//target partition
	Database db1 = data.getDatabase(targetPartition, [user, rated, ratedTrain,
		sim_lastfm_items, sim_jaccard_items, sim_tags_items,
		sim_cosine_users, sim_jaccard_users,
		users_are_friends,
		item_has_tag, item_is_popular] as Set, evidencePartitionCommon, evidencePartition);


	println "About to perform inference - Time " + new Date();

	//run MPE inference
	//------------- start
	MPEInference inferenceApp = new MPEInference(m, db1, config);
	MemoryFullInferenceResult inf_result = inferenceApp.mpeInference();
	//call the getGroundRuleStore which will enable us to get access to the ground rules
	GroundRuleStore groundRuleStore = inferenceApp.getGroundRuleStore();
	//to print the doubles with 3 decimal digits
	DecimalFormat df3 = new DecimalFormat(".###");

	//generate the recommendations
	GeneratePredictedUserRatingItem(groundRuleStore, predicted_user_rating_item);
		
	//print some stats for incompatibility and infeasibility
	if(inf_result.getTotalWeightedIncompatibility()!=null)
		println "[DEBUG inference]: Incompatibility = " + df3.format(inf_result.getTotalWeightedIncompatibility())
	if(inf_result.getInfeasibilityNorm()!=null)
		println "[DEBUG inference]: Infeasibility = " + df3.format(inf_result.getInfeasibilityNorm())

	inferenceApp.close();

	int topK = 20 //print the top 5 for each user
	
	//after updating the structures then we can print those
	for(String user : predicted_user_rating_item.keySet()){
		println "\nFor User: " + user + " Recommend:"
		//for this user create a structure which contains only the top K items
		if(predicted_user_rating_item.containsKey(user)){
			TreeMultimap<Double, String> rating_item = predicted_user_rating_item.get(user);
			printRecommendations(rating_item, item_grounding, topK);
		}
	}

	println "Done with the inference - Time " + new Date();

	//call the garbage collector - just in case!
	System.gc();

	db1.close();
	data.close();

	
	
}
		
		
	 
	
void GeneratePredictedUserRatingItem(GroundRuleStore groundRuleStore,  
	HashMap<String, TreeMultimap<Double, String>> user_rating_item){
	//to print the doubles with 3 decimal digits
	DecimalFormat df3 = new DecimalFormat(".###");
	for(GroundRule k : groundRuleStore.getGroundRules()){
		Set<Atom> atoms = k.atoms //get the atoms
		for(Atom atom : atoms){
			if (atom instanceof RandomVariableAtom){
				String user = atom.arguments[0].toString()
				String item = atom.arguments[1].toString()
				Double value = Double.parseDouble((atom.getValue()).toString())
				updateRecSysStructures(user_rating_item, user, item, value);
			}
		}
		
	}
	println "---Size of the structure user_rating_item = " + user_rating_item.size();
	
	//println user_rating_item;

}
	
	
	
	PSLModel createModel(Object object, DataStore data,
		int prior, int sim_jaccard_artists_weight, int sim_lastfm_artists_weight, int sim_tag_artists_weight,
		int sim_cosine_users_weight, int sim_jaccard_users_weight, int friends_weight,
		double item_popular_weight){
		
		
		PSLModel m = new PSLModel(this, data)
		
		//DEFINITION OF THE MODEL
		//general predicates
		m.add predicate: "user",			types: [ConstantType.UniqueIntID]	//the users
		
		m.add predicate: "rating",			types: [ConstantType.UniqueIntID, ConstantType.UniqueIntID]	//the ground truth and predictions
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
		
		m.add rule : (ratedTrain(U,I1) & rated(U,I1) & rated(U,I2) & rating(U,I1) & item_has_tag(I1,T) & item_has_tag(I2,T)) >> rating(U,I2), weight: 0.5, squared:true;
		
		
		//SOCIAL RULES
		//friendships
		m.add rule : (/*ratedTrain(U1,I) &*/ user(U2) & rated(U2,I) & rated(U1,I) & users_are_friends(U1,U2) & rating(U1,I)) >> rating(U2,I), weight: friends_weight, squared:true;
		
		//if the item in among the top 1,000 in the last.fm database then any user might like it
		m.add rule : (user(U) & rated(U,I) & item_is_popular(I)) >> rating(U,I), weight: item_popular_weight, squared: true;
		
		
		//SIMILARITIES
		
		
		//ITEMS
		//sim jacacrd (ratings)
		m.add rule :  (user(U) & ratedTrain(U,I1) & rated(U,I1) & rated(U,I2) & rating(U,I1) & sim_jaccard_items(I1,I2)) >> rating(U,I2), weight: sim_jaccard_artists_weight, squared:true;
		
		//sim last.fm
		m.add rule :  (user(U) & ratedTrain(U,I1) & rated(U,I1) & rated(U,I2) & rating(U,I1) & sim_lastfm_items(I1,I2)) >> rating(U,I2), weight: sim_lastfm_artists_weight, squared:true;
		
		//sim jaccard (tags)
		m.add rule :  (user(U) & ratedTrain(U,I1) & rated(U,I1) & rated(U,I2) & rating(U,I1) & sim_tags_items(I1,I2)) >> rating(U,I2), weight: sim_tag_artists_weight, squared:true;
		
		//USERS
		//cosine users
		m.add rule :  (/*ratedTrain(U1,I) &*/ user(U2) & rated(U1,I) & rated(U2,I) & rating(U1,I) & sim_cosine_users(U1,U2)) >> rating(U2,I), weight: sim_cosine_users_weight, squared:true;
		
		//jaccard users
		m.add rule :  (/*ratedTrain(U1,I) &*/ user(U2) & rated(U1,I) & rated(U2,I) & rating(U1,I) & sim_jaccard_users(U1,U2)) >> rating(U2,I), weight: sim_jaccard_users_weight, squared:true;
		
		//CONTENT
		//tags
		
		
		m.add rule : ~rating(U,I), weight: prior, squared: true;
	
		return m;
		
	}
	


String loadEvidenceData(Partition evidencePartition, DataStore data, String userDir, int fold, String set){
		
		println "Time before reading the files for the evidence partition common" + new Date();
	
		println "Load the users for validation"
		Inserter inserter = data.getInserter(user, evidencePartition)
		inserter.loadDelimitedData(userDir + "users.fold" + fold + "." + set);
		
		//item similarities
	
		//simialrities: item-item jaccard
		println "About to load the item-item jaccard similarities"
		inserter = data.getInserter(sim_jaccard_items, evidencePartition)
		inserter.loadDelimitedDataTruth(userDir + "artist-artist.user.jaccard.top20.ones");

		//similarities: item-item last.fm
		println "About to load the item-item last.fm similarities"
		inserter = data.getInserter(sim_lastfm_items, evidencePartition)
		inserter.loadDelimitedDataTruth(userDir + "artist-artist.lastfm.top20.ones");

		//similarities: item-item tag
		println "About to load the item-item tag similarities"
		inserter = data.getInserter(sim_tags_items, evidencePartition)
		inserter.loadDelimitedDataTruth(userDir + "artist-artist.tags.jaccard.top20.ones");

		//user similarities
	
		//similarities: user-user cosine
		println "About to load the user-user artist cosine similarities"
		inserter = data.getInserter(sim_cosine_users, evidencePartition)
		inserter.loadDelimitedDataTruth(userDir + "inputuser-user.artist.cosine.top20.ones");

		//similarities: user-user jaccard
		println "About to load the user-user artist jaccard similarities"
		inserter = data.getInserter(sim_jaccard_users, evidencePartition)
		inserter.loadDelimitedDataTruth(userDir + "inputuser-user.artist.jaccard.top20.ones");
	
		//similarities: friend-friend cosine
		println "About to load the friend-friend cosine similarities"
		inserter = data.getInserter(users_are_friends, evidencePartition)
		//inserter8.loadDelimitedDataTruth(userDir + "inputfriends-user-user.artist.cosine.binary");
		inserter.loadDelimitedData(userDir + "inputuser_friend.graph");
		//here test the size of the structure if it is less than 10 we need to send error message.
		
		//tags
		println "About to load the artist has tag"
		inserter = data.getInserter(item_has_tag, evidencePartition)
		inserter.loadDelimitedData(userDir + "artist-tags");
	
		//top artists in last.fm database
		println "About to load the top 1,000 artists in the last.fm database"
		inserter = data.getInserter(item_is_popular, evidencePartition)
		inserter.loadDelimitedData(userDir + "top_1000_artists.id.only");
	
		
		println "About to load the existing ratings of the system"
		inserter = data.getInserter(rating, evidencePartition);
		inserter.loadDelimitedData(userDir + "rating_train");

		return "";
}

						


String loadEvidenceData(Partition evidencePartition, DataStore data, String userDir, String ratedTrainData, String ratedData){
		
		println "Time before reading the files for the evidence partition" + new Date();
	
		println "About to load the data for the predicate ratedTrain"
		Inserter inserter = data.getInserter(ratedTrain, evidencePartition); //this should contain the entries of the "rating_train" plus the "to_predict"
		inserter.loadDelimitedData(userDir + ratedTrainData);

		println "About to load the data for the predicate rated"
		inserter = data.getInserter(rated, evidencePartition);	//load file: "rated"
		inserter.loadDelimitedData(userDir + ratedData);

		return "";
}



String loadTargetData(Partition targetPartition, DataStore data, String userDir, String ratedTrainData){
	
	println "Time before reading the files for the target partition " + new Date();

	println "About to load the ratings I want to predict"
	Inserter inserter = data.getInserter(rating, targetPartition)
	inserter.loadDelimitedData(userDir + ratedTrainData);

	return "";
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
   
		
void updateRecSysStructures(HashMap<String,TreeMultimap<Double, String>> user_rating_item, 
							String user, String item, Double value){
	
	//println "(" + user + "," + item + ")=" + value;
	
	//if the user is already contained in the structure
	if(!user_rating_item.containsKey(user)){
		//create the structure where I will store the pairs "rating, item"
		final Comparator<Double> DECREASING_DOUBLE_COMPARATOR = Ordering.<Double>natural().reverse().nullsFirst();
		TreeMultimap<Double, String> rating_item = new TreeMultimap<>(DECREASING_DOUBLE_COMPARATOR, Ordering.natural());
		//insert the rating in the structure
		rating_item.put(value, item)
		//insert this user in the structure 
		user_rating_item.put(user, rating_item)
		
	}
	else{	//if the user is not contained in the structure
		//retrieve the structure rating_item
		TreeMultimap<Double, String> rating_item = user_rating_item.get(user);
		rating_item.put(value, item)
	}
	
}
	
		 
	
void printRecommendations(TreeMultimap<Double, String> sortedRatings_items, 
							HashMap<String, HashSet<String>> items_groundings, int topK){

		//println "\nRecommend items: "
		int i=1;
		
		Set<Double> ratings = sortedRatings_items.keySet();
		for(Double rating : ratings){
			Set<String> items = sortedRatings_items.get(rating);
			for(String item : items){
				if(i<=topK){
					println i + "." + item + " value=" + rating //+ " with the groundings "
					i++; 
				}
			}
		}
}
		
	

public static void main(String[] args) {
    LastFMEvaluate5Folds lfm = new LastFMEvaluate5Folds() ;
    lfm.doit(args[0]);
    // done
}
	
	
}
