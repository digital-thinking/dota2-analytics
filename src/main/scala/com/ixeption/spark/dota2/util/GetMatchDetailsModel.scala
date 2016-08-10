package com.ixeption.spark.dota2.util

import spray.json.DefaultJsonProtocol


/**
  * Created by ixeption on 27.06.2016.
  */
object GetMatchDetailsModel {

  case class Ability_upgrades(
                               ability: Double,
                               time: Double,
                               level: Double
                             )

  case class Players(
                      account_id: Double,
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
                      leaver_status: Double,
                      last_hits: Double,
                      denies: Double,
                      gold_per_min: Double,
                      xp_per_min: Double,
                      level: Double,
                      gold: Double,
                      gold_spent: Double,
                      hero_damage: Double,
                      ability_upgrades: List[Ability_upgrades]
                    )

  case class ResultBis(
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

  case class MatchDetails(
                           result: ResultBis
                         )

  object Ability_upgradesProtocol extends DefaultJsonProtocol {
    implicit val MatchesFormat = jsonFormat3(Ability_upgrades)
  }

  object PlayersProtocol extends DefaultJsonProtocol {

    import com.ixeption.spark.dota2.util.GetMatchDetailsModel.Ability_upgradesProtocol._

    implicit val PlayersFormat = jsonFormat22(Players)
  }


  object ResultBisProtocol extends DefaultJsonProtocol {

    import com.ixeption.spark.dota2.util.GetMatchDetailsModel.PlayersProtocol._

    implicit val ResultBisFormat = jsonFormat22(ResultBis)
  }

  object MatchDetailsProtocol extends DefaultJsonProtocol {

    import com.ixeption.spark.dota2.util.GetMatchDetailsModel.ResultBisProtocol._

    implicit val MatchHistoryFormat = jsonFormat1(MatchDetails)
  }


}