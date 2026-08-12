package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.ChannelDto
import pm.antani.resentin.net.dto.NetworkDto
import retrofit2.http.GET
import retrofit2.http.Path

interface NetworksApi {
    @GET("networks")
    suspend fun getNetworks(): List<NetworkDto>

    @GET("networks/{slug}/channels")
    suspend fun getChannels(@Path("slug") slug: String): List<ChannelDto>
}
