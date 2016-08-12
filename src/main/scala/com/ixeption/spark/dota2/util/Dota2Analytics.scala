package com.ixeption.spark.dota2.util

import org.apache.spark.ml.classification.MultilayerPerceptronClassifier
import org.apache.spark.ml.clustering.KMeans
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.feature.LabeledPoint
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DoubleType, IntegerType}
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.math.BigDecimal.RoundingMode


/**
  * Created by ixeption on 01.07.2016.
  */
object Dota2Analytics {

  val isWinner = udf((radiant_win: Boolean, player_slot: Double) => {
    val isRadian = if (player_slot < 128) true else false
    radiant_win == isRadian
  })
  val seqToSparseBag = udf((seq: Seq[Double], int: Int) => {
    val occurances = seq.groupBy(l => l).map(t => (t._1.toInt, t._2.length.toDouble)).toSeq
    Vectors.sparse(int, occurances)
  })

  val getMatchVec = udf((seq: Seq[Double], seq2: Seq[Double], size: Int) => {
    val occurances1 = seq.groupBy(l => l).map(t => (t._1.toInt, t._2.length.toDouble)).toSeq
    val occurances2 = seq2.groupBy(l => l).map(t => (t._1.toInt, t._2.length.toDouble)).toSeq
    Vectors.sparse(size + size, occurances1 ++ occurances2)
  })

  val seqToDense = udf((array: Seq[Double]) => {
    Vectors.dense(array.toArray)
  })

  val percentage = udf((groupCount: Double, totalCount: Double) => {
    BigDecimal(100 * groupCount / totalCount).setScale(0, RoundingMode.HALF_UP).toInt
  })

  val toRole = udf((id: Int) => id match {
    case 0 => "ganker"
    case 1 => "carry"
    case 2 => "support"
  })

  def convertToVec(spark: SparkSession, matchesDf: DataFrame): DataFrame = {
    matchesDf

  }

  def findMostPlayedHeros(matchesDf: DataFrame, heros: DataFrame): Unit = {
    import matchesDf.sqlContext.implicits._
    val herosIdName = heros.select($"col.id", $"col.localized_name")
    val playersDf: DataFrame = matchesDf.select(explode(matchesDf("col.players"))).toDF()

    val heroCounts = playersDf.select("col.hero_id")
      .groupBy("hero_id")
      .count()
      .join(herosIdName, $"hero_id" === $"id")
      .coalesce(1)
      .sort($"count".desc)

    val totalCount = playersDf.count().toDouble / 100.0
    val percentage = heroCounts.select($"hero_id", $"localized_name", ($"count" / totalCount).as("percent"))
    percentage.show(10)

  }

  def predictWinByItems(matchesDf: DataFrame, items: DataFrame): Unit = {
    import matchesDf.sqlContext.implicits._
    val playerWithWinStatus = matchesDf
      .select($"col.match_id", $"col.radiant_win", explode($"col.players").as("players"))
      .withColumn("winner", isWinner($"radiant_win", $"players.player_slot"))
      .drop("radiant_win")
      .cache()

    val itemCount = items.count().toInt
    val vectors = playerWithWinStatus
      .select($"winner", $"players.hero_id", $"players.item_0", $"players.item_1", $"players.item_2", $"players.item_3", $"players.item_4", $"players.item_5")
      .rdd.map(row => {
      val seq: Seq[Int] = Seq[Int](
        row.getAs("item_0").asInstanceOf[Double].toInt,
        row.getAs("item_1").asInstanceOf[Double].toInt,
        row.getAs("item_2").asInstanceOf[Double].toInt,
        row.getAs("item_3").asInstanceOf[Double].toInt,
        row.getAs("item_4").asInstanceOf[Double].toInt,
        row.getAs("item_5").asInstanceOf[Double].toInt)
      val occurances = seq.groupBy(l => l).map(t => (t._1, t._2.length.toDouble)).toSeq
      val winnerValue = if (row.getAs("winner").asInstanceOf[Boolean]) 1.0 else 0.0
      LabeledPoint(winnerValue, Vectors.sparse(itemCount, occurances))
    })

    val splits = vectors.randomSplit(Array(0.7, 0.3))
    val (trainingData, testData) = (splits(0), splits(1))


    val numClasses = 2
    val categoricalFeaturesInfo = Map[Int, Int]((0, itemCount), (1, itemCount), (2, itemCount), (3, itemCount), (4, itemCount), (5, itemCount))
    val numTrees = 256
    val featureSubsetStrategy = "auto"
    val impurity = "gini"
    val maxDepth = 10
    val maxBins = itemCount

    //    val model = RandomForest.trainClassifier(trainingData, numClasses, categoricalFeaturesInfo,
    //      numTrees, featureSubsetStrategy, impurity, maxDepth, maxBins)
    //
    //    val labelAndPreds = testData.map { point =>
    //      val prediction = model.predict(point.features)
    //      (point.label, prediction)
    //    }
    //
    //    val testErr = labelAndPreds.filter(r => r._1 != r._2).count.toDouble / testData.count()
    //    println("Test Error = " + testErr)
  }

  def predictWinByTeam(matchesDf: DataFrame, heros: DataFrame): Unit = {
    import matchesDf.sqlContext.implicits._
    val playerWithWinStatus = matchesDf
      .select($"col.match_id", $"col.radiant_win", explode($"col.players").as("players"))
      .withColumn("winner", isWinner($"radiant_win", $"players.player_slot"))
      .drop("radiant_win")
      .cache()

    val heroCount = heros.count().toInt
    playerWithWinStatus.printSchema()

    val winMatchVecDf = playerWithWinStatus
      .select($"winner".cast(DoubleType).as("label"), $"match_id".cast(IntegerType), $"players.hero_id".as("hero"))
      .groupBy($"label", $"match_id")
      .agg(collect_list($"hero").as("herolist"))
      .withColumn("features", seqToSparseBag($"herolist", lit(heroCount)))
      .drop("herolist")

    val splits = winMatchVecDf.randomSplit(Array(0.7, 0.3))
    val (trainingData, testData) = (splits(0), splits(1))

    val layers = Array[Int](heroCount, 10, 10, 2)

    val trainer = new MultilayerPerceptronClassifier()
      .setLayers(layers)
      .setBlockSize(64)
      .setSeed(1234L)
      .setTol(1E-4)
      .setMaxIter(100)

    val model = trainer.fit(trainingData)
    val result: DataFrame = model.transform(testData)

    val predictionAndLabels = result.select("prediction", "label")
    val evaluator = new MulticlassClassificationEvaluator()
      .setMetricName("accuracy")
    println("Accuracy: " + evaluator.evaluate(predictionAndLabels))

  }

  def clusterHeros(matchesDf: DataFrame, heros: DataFrame): Unit = {
    import matchesDf.sqlContext.implicits._
    val herosIdName = heros.select($"col.id", $"col.localized_name")
    val heroStats = matchesDf
      .select(explode($"col.players").as("players"), $"col.radiant_win")
      .withColumn("winner", isWinner($"radiant_win", $"players.player_slot"))
      //.filter($"winner" === true)
      .select($"players.hero_id".cast(DoubleType), array($"players.kills".cast(DoubleType), $"players.assists".cast(DoubleType), $"players.deaths".cast(DoubleType), $"players.gold_per_min".cast(DoubleType), $"players.last_hits".cast(DoubleType), $"players.xp_per_min".cast(DoubleType)).as("arrayFeatures"))
      //removed $"players.gold".cast(DoubleType), $"players.tower_damage".cast(DoubleType), $"players.hero_damage".cast(DoubleType),$"players.tower_damage".cast(DoubleType)
      .withColumn("features", seqToDense($"arrayFeatures"))
      .drop($"arrayFeatures")
      .cache()

    val count: Long = heroStats.count()

    val clusterer = new KMeans()
      //.setK(heros.count().toInt)
      .setK(3)
      .setSeed(1L)
      .setMaxIter(100)
      .setInitMode("random")

    val model = clusterer.fit(heroStats)
    val transform: DataFrame = model.transform(heroStats).cache()

    val heroRoleCounts = transform
      .groupBy($"hero_id", $"prediction")
      .count()

    val heroCounts = transform
      .groupBy($"hero_id")
      .count()

    val joinedData = heroRoleCounts.join(heroCounts, heroRoleCounts("hero_id") === heroCounts("hero_id"))
      .drop(heroCounts("hero_id"))
      .withColumn("percentage", percentage(heroRoleCounts("count"), heroCounts("count")))


    val pretty = joinedData
      .join(herosIdName, $"hero_id" === $"id")
      .withColumn("predictedRole", toRole($"prediction"))
      .sort($"localized_name".asc)
      .drop("hero_id", "prediction", "features", "count", "id")
      .select("localized_name", "predictedRole", "percentage")


    pretty.show()
    pretty.coalesce(1).write.option("header", "true").csv("output/resultRoles")

    println("Number of records " + count)
    println("Clusters found")
    model.clusterCenters.foreach(println)
  }

  def run(spark: SparkSession, matchesDf: DataFrame, heros: DataFrame, items: DataFrame) {
    matchesDf.printSchema()
    //predictWinByItems(matchesDf, items)
    //predictWinByTeam(matchesDf, heros)
    //findMostPlayedHeros(matchesDf, heros)
    //clusterHeros(matchesDf, heros)
    predictWinByBothTeams(matchesDf, heros)


  }

  def predictWinByBothTeams(matchesDf: DataFrame, heros: DataFrame): Unit = {
    import matchesDf.sqlContext.implicits._
    val heroCount = heros.count().toInt
    val explodedDf = matchesDf
      .filter($"col.radiant_win" === true)
      .select($"col.match_id", explode($"col.players").as("players"))
      .cache()

    val winnerTeam = explodedDf.filter($"players.player_slot" < 128)
      .groupBy($"match_id")
      .agg(collect_list($"players.hero_id").as("winnerTeam"))
      .cache()

    val looserTeam = explodedDf.filter($"players.player_slot" >= 128)
      .groupBy($"match_id")
      .agg(collect_list($"players.hero_id").as("looserTeam"))
      .cache()

    val joined = winnerTeam
      .join(looserTeam, winnerTeam("match_id") === looserTeam("match_id"))
      .drop(looserTeam("match_id"))
      .select("match_id", "winnerTeam", "looserTeam")

    //joined.coalesce(1).write.json("output/joined")

    val winVecs = joined.select(getMatchVec($"winnerTeam", $"looserTeam", lit(heroCount)).as("features"))
      .withColumn("label", lit(1.0))

    val looseVecs = joined.select(getMatchVec($"looserTeam", $"winnerTeam", lit(heroCount)).as("features"))
      .withColumn("label", lit(0.0))

    val allvecs = looseVecs.union(winVecs)
    val splits = allvecs.randomSplit(Array(0.7, 0.3))
    val (trainingData, testData) = (splits(0), splits(1))

    val layers = Array[Int](heroCount * 2, 15, 2)

    val trainer = new MultilayerPerceptronClassifier()
      .setLayers(layers)
      .setBlockSize(64)
      .setSeed(1234L)
      .setTol(1E-4)
      .setMaxIter(100)

    //val trainer = new NaiveBayes()

    val model = trainer.fit(trainingData)
    val result: DataFrame = model.transform(testData)

    val predictionAndLabels = result.select("prediction", "label")
    val evaluator = new MulticlassClassificationEvaluator()
      .setMetricName("accuracy")
    println("Accuracy: " + evaluator.evaluate(predictionAndLabels))

  }

}
