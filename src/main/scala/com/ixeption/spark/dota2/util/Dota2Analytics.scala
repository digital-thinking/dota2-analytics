package com.ixeption.spark.dota2.util

import org.apache.spark.ml.classification.MultilayerPerceptronClassifier
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.feature.LabeledPoint
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DoubleType, IntegerType}
import org.apache.spark.sql.{DataFrame, SparkSession}


/**
  * Created by ixeption on 01.07.2016.
  */
object Dota2Analytics {

  val isWinner = udf((radiant_win: Boolean, player_slot: Double) => {
    val isRadian = if (player_slot >= 128) true else false
    radiant_win == isRadian
  })
  val seqToVec = udf((seq: Seq[Double], int: Int) => {
    val occurances = seq.groupBy(l => l).map(t => (t._1.toInt, t._2.length.toDouble)).toSeq
    Vectors.sparse(int, occurances)
  })

  def convertToVec(spark: SparkSession, matchesDf: DataFrame): DataFrame = {
    matchesDf

  }

  def findMostPlayedHeros(matchesDf: DataFrame, heros: DataFrame): DataFrame = {
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
    percentage
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

  def run(spark: SparkSession, matchesDf: DataFrame, heros: DataFrame, items: DataFrame) {
    matchesDf.printSchema()
    //predictWinByItems(matchesDf, items)
    predictWinByTeam(matchesDf, heros)
    //findMostPlayedHeros(matchesDf, heros)


  }

  def predictWinByTeam(matchesDf: DataFrame, heros: DataFrame): DataFrame = {
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
      .withColumn("features", seqToVec($"herolist", lit(heroCount)))
      .drop("herolist")

    val splits = winMatchVecDf.randomSplit(Array(0.7, 0.3))
    val (trainingData, testData) = (splits(0), splits(1))

    val layers = Array[Int](111, 10, 10, 2)

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
    playerWithWinStatus
  }


}
