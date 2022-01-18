import java.io.Serializable

data class User(
        var username: String ="",
        var nome: String ="",
        var email: String = "",
        var password: String = "",
        var morada: String = ""
):Serializable
