import org.modelcatalogue.core.api.ElementStatus
import org.modelcatalogue.core.security.User

fixture {
    U_Bella(User, name: "Bella", username: "Bella", password: "password", status: ElementStatus.FINALIZED)
}

