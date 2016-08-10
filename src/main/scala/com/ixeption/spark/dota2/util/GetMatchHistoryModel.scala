package com.ixeption.spark.dota2.util

import spray.json.DefaultJsonProtocol


/**
  * Created by ixeption on 27.06.2016.
  */

object GetMatchHistoryModel {

  case class Players(
                      account_id: Double,
                      player_slot: Double,
                      hero_id: Double
                    )

  case class Matches(
                      match_id: Double,
                      match_seq_num: Double,
                      start_time: Double,
                      lobby_type: Double,
                      radiant_team_id: Double,
                      dire_team_id: Double,
                      players: Seq[Players]
                    )

  case class ResultBis(
                        status: Double,
                        num_results: Double,
                        total_results: Double,
                        results_remaining: Double,
                        matches: Seq[Matches]
                      )

  case class MatchHistory(
                           result: ResultBis
                         )

  object SkillLevel extends Enumeration {
    type SkillLevel = Value
    val Any = Value(0)
    val Normal = Value(1)
    val High = Value(2)
    val VeryHigh = Value(3)
  }

  object PlayersProtocol extends DefaultJsonProtocol {
    implicit val PlayersFormat = jsonFormat3(Players)
  }

  object MatchesProtocol extends DefaultJsonProtocol {

    import com.ixeption.spark.dota2.util.GetMatchHistoryModel.PlayersProtocol._

    implicit val MatchesFormat = jsonFormat7(Matches)
  }

  object ResultBisProtocol extends DefaultJsonProtocol {

    import com.ixeption.spark.dota2.util.GetMatchHistoryModel.MatchesProtocol._

    implicit val ResultBisFormat = jsonFormat5(ResultBis)
  }

  object MatchHistoryProtocol extends DefaultJsonProtocol {

    import com.ixeption.spark.dota2.util.GetMatchHistoryModel.ResultBisProtocol._

    implicit val MatchHistoryFormat = jsonFormat1(MatchHistory)
  }


}