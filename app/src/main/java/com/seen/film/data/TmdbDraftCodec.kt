package com.seen.film.data

import com.seen.film.domain.MetadataLanguage
import org.json.JSONArray
import org.json.JSONObject

/** Private draft serialization, separate from the public backup protocol. */
internal fun TmdbMovie.draftJson(): JSONObject {
    val cast=JSONArray();val crew=JSONArray()
    for(c in credits){
        val item=JSONObject().put("id",c.personId).put("credit_id",c.creditKey).put("name",c.name)
            .put("department",c.department?:JSONObject.NULL).put("job",c.job?:JSONObject.NULL)
            .put("character",c.character?:JSONObject.NULL).put("profile_path",c.profilePath?:JSONObject.NULL)
        if(c.isCast)cast.put(item)else crew.put(item)
    }
    val genresJson=JSONArray();genres.forEach{genresJson.put(JSONObject().put("id",it.id).put("name",it.name))}
    return JSONObject().put("id",id).put("title",title).put("original_title",originalTitle?:JSONObject.NULL)
        .put("release_date",releaseDate?:JSONObject.NULL).put("poster_path",posterPath?:JSONObject.NULL)
        .put("overview",overview?:JSONObject.NULL).put("runtime",runtime?:JSONObject.NULL)
        .put("genres",genresJson).put("credits",JSONObject().put("cast",cast).put("crew",crew))
        .put("draftDirectors",directors?:JSONObject.NULL).put("draftCountries",countries?:JSONObject.NULL).put("draftLanguage",language.apiCode)
}
internal fun readTmdbDraft(json:JSONObject):TmdbMovie=TmdbRepository.parse(json,MetadataLanguage.fromCode(json.optString("draftLanguage","zh-CN"))).copy(
    directors=if(json.isNull("draftDirectors"))null else json.getString("draftDirectors"),
    countries=if(json.isNull("draftCountries"))null else json.getString("draftCountries"))
