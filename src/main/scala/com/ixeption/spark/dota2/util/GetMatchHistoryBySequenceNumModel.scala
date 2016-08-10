package com.ixeption.spark.dota2.util

import spray.json.DefaultJsonProtocol


/**
  * Created by ixeption on 27.06.2016.
  */
object GetMatchHistoryBySequenceNumModel {

  case class Players(
                      account_id: Option[Double],
                      player_slot: Double,
                      hero_id: Double,
                      item_0: Double,
                      item_1: Double,
                      item_2: Double,
                      item_3: Double,
                      item_4: Double,
                      item_5: Double,
                      kills: Double,
                      deaths: Double,
                      assists: Double,
                      leaver_status: Option[Double],
                      last_hits: Double,
                      denies: Double,
                      gold_per_min: Double,
                      xp_per_min: Double,
                      level: Double,
                      gold: Option[Double],
                      gold_spent: Option[Double],
                      hero_damage: Option[Double],
                      tower_damage: Option[Double]
                    )

  case class Matches(
                      players: List[Players],
                      radiant_win: Boolean,
                      duration: Double,
                      pre_game_duration: Double,
                      start_time: Double,
                      match_id: Double,
                      match_seq_num: Double,
                      tower_status_radiant: Double,
                      tower_status_dire: Double,
                      barracks_status_radiant: Double,
                      barracks_status_dire: Double,
                      cluster: Double,
                      first_blood_time: Double,
                      lobby_type: Double,
                      human_players: Double,
                      leagueid: Double,
                      positive_votes: Double,
                      negative_votes: Double,
                      game_mode: Double,
                      flags: Double,
                      radiant_score: Double,
                      dire_score: Double
                    )

  case class ResultBis(
                        status: Double,
                        matches: List[Matches]
                      )

  case class GetMatchHistoryBySequenceNum(
                                           result: ResultBis
                                         )

  object PlayersProtocol extends DefaultJsonProtocol {
    implicit val PlayersFormat = jsonFormat22(Players)
  }

  object MatchesProtocol extends DefaultJsonProtocol {

    import GetMatchHistoryBySequenceNumModel.PlayersProtocol._

    implicit val MatchesFormat = jsonFormat22(Matches)
  }

  object ResultBisProtocol extends DefaultJsonProtocol {

    import GetMatchHistoryBySequenceNumModel.MatchesProtocol._

    implicit val ResultBisFormat = jsonFormat2(ResultBis)
  }

  object MatchSequenceProtocol extends DefaultJsonProtocol {

    import GetMatchHistoryBySequenceNumModel.ResultBisProtocol._

    implicit val MatchSequenceFormat = jsonFormat1(GetMatchHistoryBySequenceNum)
  }


}