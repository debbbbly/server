package com.debbly.server

import com.google.protobuf.util.JsonFormat
import io.livekit.server.RoomServiceClient
import livekit.LivekitModels
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import retrofit2.Call
import retrofit2.Response


@SpringBootTest
class ServerApplicationTests {

    @Test
    fun contextLoads() {
        val client = RoomServiceClient.createClient(
            "https://debbly.duckdns.org",
            "",
            ""
        )

        val call: Call<LivekitModels.Room> = client.createRoom("room_name")
        val response: Response<LivekitModels.Room> = call.execute()
        val room: LivekitModels.Room? = response.body()



        System.out.println(JsonFormat.printer().print(room))
    }


}
