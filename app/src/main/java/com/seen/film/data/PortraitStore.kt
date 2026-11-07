package com.seen.film.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Rebuildable bounded portrait cache; never contains personal images or backup assets. */
class PortraitStore(context:Context,private val tmdb:TmdbRepository){
    private val directory=File(context.filesDir,"people-cache")
    private val mutex=Mutex()
    suspend fun file(path:String):File?=withContext(Dispatchers.IO){mutex.withLock{
        require(path.matches(Regex("/[A-Za-z0-9._-]+")))
        val key=MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString(""){"%02x".format(it)}+".jpg"
        val target=File(directory,key)
        if(target.isFile){target.setLastModified(System.currentTimeMillis());return@withLock target}
        try{
            val bytes=tmdb.portrait(path)
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            require(bounds.outWidth>0&&bounds.outHeight>0)
            val options=BitmapFactory.Options().apply{inSampleSize=1;while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize>256)inSampleSize*=2}
            val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)?:return@withLock null
            var temp:File?=null
            try{
                check(directory.mkdirs()||directory.isDirectory)
                temp=File.createTempFile("portrait-",".tmp",directory)
                temp.outputStream().use{require(bitmap.compress(Bitmap.CompressFormat.JPEG,88,it))}
                check(temp.renameTo(target))
            }finally{bitmap.recycle();temp?.delete()}
            var total=directory.listFiles().orEmpty().sumOf{it.length()}
            for(old in directory.listFiles().orEmpty().sortedBy{it.lastModified()}){
                if(total<=20L*1024*1024)break
                if(old!=target&&old.delete())total-=old.length()
            }
            target
        }catch(e:CancellationException){throw e}catch(_:Exception){target.delete();null}
    }}
}
