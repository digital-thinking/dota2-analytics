import com.ixeption.spark.dota2.util.Dota2Analytics
import org.apache.spark.SparkConf
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.junit.{Before, Test}


/**
  * Created by Christian on 05.06.2016.
  */

@Test
object LocalSparkRunner {
  @Before
  def prepare(): Unit = {
    System.setProperty("hadoop.home.dir", "C:\\Users\\Christian\\Dev\\hadoop-2.6.0")
  }

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Dota2")
    conf.set("spark.master", "local[4]")
    conf.set("spark.sql.warehouse.dir", "file:///C:/Users/Christian/AppData/Local/Temp/spark-warehouse")
    //conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    //conf.set("spark.kryoserializer.buffer.max", "512m")
    val spark: SparkSession = SparkSession.builder().appName("Dota2").config(conf).getOrCreate()

    val dataFrame: DataFrame = spark.read.json("src/main/resources/data/matches.json")
    //val dataFrame: DataFrame = spark.read.parquet("matchespar")
    import dataFrame.sqlContext.implicits._
    val items: DataFrame = spark.read.json("src/main/resources/data/items_23_06_2016.json")
      .select(explode($"result.items"))
      .toDF()
    broadcast(items)
    val heros: DataFrame = spark.read.json("src/main/resources/data/heros_01_07_2016.json")
      .select(explode($"result.heroes"))
      .toDF()
    broadcast(heros)
    val matchesDf = dataFrame.select(explode($"matches")).toDF()
    Dota2Analytics.run(spark, matchesDf, heros, items)

    spark.stop()

  }

}
