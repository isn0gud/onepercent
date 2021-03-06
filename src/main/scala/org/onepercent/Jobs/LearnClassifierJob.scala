/**
 * The MIT License (MIT) Copyright (c) 2014 University of Applied Sciences, Berlin, Germany
 * For more detailed information, please read the licence.txt in the root directory.
 **/

package org.onepercent.Jobs

import java.text.SimpleDateFormat
import java.util.Calendar

import org.apache.spark.sql.SchemaRDD
import org.apache.spark.sql.catalyst.expressions.GenericMutableRow
import org.apache.spark.sql.hive.HiveContext
import org.onepercent.utils.Types.TypeCreator
import org.onepercent.utils.scoring.{ScoringTrainingSample, TrainedData, TweetScoringLearner}
import org.onepercent.utils.{Config, ErrorMessage, JsonTools, _}
import org.onepercent.{Env, JobExecutor, JobResult}

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

/**
 * Success message of the learn classifier job.
 * @param msg Success message.
 */
case class TrainResult(msg: String) extends JobResult

/**
 * Learns a given set of training data for scoring tweets.
 * @see http://nlp.stanford.edu/IR-book/html/htmledition/naive-bayes-text-classification-1.html
 * @author pFriesch
 */
class LearnClassifierJob extends JobExecutor with Logging {

  /**
   * Type category is a representation of a string to clearify the use in the source code.
   */
  type Category = String

  /**
   * Fetches given training data, computes a category probability and a term probability of the training data.
   * The trained data is saved as json to the scoring_TrainedDataPath as given in the config.
   * @see http://nlp.stanford.edu/IR-book/html/htmledition/naive-bayes-text-classification-1.html
   * @param params the params of the specified job.
   * @return a positive jobResult or an ErrorMessage if an error occurred while executing
   */
  override def executeJob(params: List[String]): JobResult = {
    if (params.length > 0) ErrorMessage("Job does not accept parameters", 100)
    else {
      // the method needs to be exchanged based on the resource of the training data
      //      println(fetchTweetTrainingData())
      Try(fetchTrainingData()) match {
        case util.Success(data) => {
          val tweetScoringLearner = new TweetScoringLearner(Env.sc)
          val trainedData: TrainedData = tweetScoringLearner.learn(data)
          Try(JsonTools.writeToFileAsJson(trainedData, Config.get.scoring_TrainedDataPath)) match {
            case Success(_) =>
              TrainResult("Trained and written Data successfully")
            case Failure(_) =>
              log("executeJob", "Failed to write trained Data to: " + Config.get.scoring_TrainedDataPath)
              ErrorMessage("Failed to write trained Data to: " + Config.get.scoring_TrainedDataPath, 101)
          }
        }
        case Failure(ex) =>
          log("executeJob", "Failed to fetch training Data: " + ex)
          ErrorMessage("Failed to fetch training Data: " + ex, 101)
      }
    }
  }

  /**
   * Fetches the hardcoded trainings data
   * @return the trainig data
   */
  private def fetchTrainingData(): Map[Category, List[String]] = {
    ScoringTrainingSample.trainingSet()
  }

  /**
   * Scrapes tweets with the given hashtags to make trained data
   * @return the training data
   */
  private def fetchTweetTrainingData(): Map[Category, List[String]] = {
    val timeFormatter: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val currentCalendar: Calendar = Calendar.getInstance()
    //set current time 10 mins ago
    currentCalendar.add(Calendar.MINUTE, -10)
    val pastCalendar: Calendar = Calendar.getInstance()
    //get date 14 days ago
    pastCalendar.add(Calendar.DAY_OF_MONTH, -14)

    val startTime: String = timeFormatter.format(pastCalendar.getTime())
    val endTime: String = timeFormatter.format(currentCalendar.getTime())

    Try(TypeCreator.gregorianCalendar(startTime, timeFormatter)) match {
      case Success(startGregCalendar) =>
        Try(TypeCreator.gregorianCalendar(endTime, timeFormatter)) match {
          case Success(endGregCalendar) =>
            Try(TypeCreator.multipleClusterPath(Config.get.tweetsPrefixPath, startGregCalendar, endGregCalendar, "*.data")) match {
              case Success(path) =>

                val hc = new HiveContext(Env.sc)
                val tweetData: SchemaRDD = new TweetJSONFileReader(Env.sc, hc).readFile(path)
                tweetData.registerTempTable("tweets")
                val tweetsWithHashtags: SchemaRDD = hc.sql("SELECT text, entities.hashtags FROM tweets WHERE lang = 'en'")
                tweetsWithHashtags.map(row => row(0) -> row(1).asInstanceOf[ArrayBuffer[GenericMutableRow]].map(h => h.toList(1)).toList).map {
                  case tweetWithHashtag: (String, List[String]) => CategoryData.toCategoryTuple(tweetWithHashtag)
                  //does not match so its not used
                  case _ => ("ERROR", "ERROR IN FILTERING TWEETS BY CATEGORY")
                }.filter(T => T._1 != "").groupBy(_._1).map(X => (X._1, X._2.map(_._2).toList)).collect().toMap

              case Failure(wrongPath) =>
                throw new IllegalArgumentException("No Data available between " + startTime + " and " + endTime)
            }
          case Failure(wrongEndTime) =>
            throw new IllegalArgumentException("Can not create past Calender")
        }
      case Failure(wrongStartTime) =>
        throw new IllegalArgumentException("Can not create current Calender")
    }
  }

}

/**
 * Helper Object for fetchTweetTrainingData method, needed for serialization in the spark cluster.
 */
object CategoryData extends Serializable {

  /**
   * Hard coded list of Categories
   */
  val categories: List[String] = List("Religion", "Sport")
  /**
   *  Hard coded list of hashtags in the categories.
   */
  val categoryHashtags: Map[String, List[String]] = Map(categories(0) ->
    List("christianity", "pope", "jesus", "christ", "christian", "buddha", "buddhist", "buddhism", "mohammed", "islam", "moslem",
      "muslim", "hinduism", "hindu", "hindoo", "judaism", "jewry", "jew", "atheist", "agnostic"),
    categories(1) -> List("archery", "badminton", "volleyball", "tennis", "baseball", "cricket", "skateboarding", "surfing",
      "climbing", "cycling", "boxing", "taekwondo", "fencing", "billiards", "snooker", "ultimate", "football",
      "rugby", "golf", "handball", "curling", "hockey", "biathlon", "triathlon", "badminton", "squash", "running",
      "sailing", "skiing", "bobsleigh", "sled", "snowboarding", "swimming", "diving"))

  /**
   * Matches a tweet with its hashtag to a category with the tweet text.
   * @param tweetWithHashtag The tweet with its hashtags.
   * @return A tuple of a Category with a tweet text which is matched to this category.
   */
  //method needs to be serializable to be send to the nodes so i put it in here, seemed to be the best way since the list
  def toCategoryTuple(tweetWithHashtag: (String, List[String])): (String, String) = {
    //checks if the tweet has a hashtag which is also in the category hashtags list
    //Religion
    if (categoryHashtags(categories(0)).exists(hashPre => tweetWithHashtag._2.exists(hashTweet => hashTweet.containsSlice(hashPre)))) {
      (categories(0), tweetWithHashtag._1)
    }
    //Sport
    else if (categoryHashtags(categories(1)).exists(hashPre => tweetWithHashtag._2.exists(hashTweet => hashTweet.containsSlice(hashPre)))) {
      (categories(1), tweetWithHashtag._1)
    }
    else ("", "")
  }
}


