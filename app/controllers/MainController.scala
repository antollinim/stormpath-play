/*
 * Copyright 2013 Stormpath, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

import models._
import views._
import scala.concurrent.Future

object MainController extends Controller {

  var loggedUser : Option[User] = None

  def updatePasswordForm(implicit request: Request[_]) = Form(
    tuple(
      "email" -> text,
      "password" -> text
    ) verifying ("Invalid email or password", result => result match {
      case (email, password) => {
        val user = User.authenticate(email, password)
        loggedUser = user
        user.isDefined
      }
    })
  )

  /**
   * Login page.
   */
  def login = Action { implicit request =>
    Ok(html.login(updatePasswordForm))
  }

  /**
   * Handle login form submission.
   */
  def authenticate = Action { implicit request =>
    updatePasswordForm.bindFromRequest.fold(
      formWithErrors => {
        loggedUser = None
        BadRequest(html.login(formWithErrors))
      },
      user  => {
        Redirect(routes.CustomDataController.index).withSession("email" -> loggedUser.get.email, "fullName" -> loggedUser.get.fullName, "customDataHref" -> loggedUser.get.customDataHref)
      }
    );
  }

  /**
   * Logout and clean the session.
   */
  def logout = Action {
    Redirect(routes.MainController.login).withNewSession.flashing(
      "success" -> "You've been logged out"
    )
  }

  // -- Javascript routing

  def javascriptRoutes = Action { implicit request =>
    import routes.javascript._
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        CustomDataController.addCustomDataItem,
        CustomDataController.deleteCustomDataItem
      )
    ).as("text/javascript") 
  }

}

/**
 * Provide security features
 */
trait Secured {
  
  /**
   * Retrieve the connected user email.
   */
  private def username(request: RequestHeader) = request.session.get("email")

  /**
   * Redirect to login if the user in not authorized.
   */
  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.MainController.login)
  
  // --
  
  /** 
   * Action for authenticated users.
   */
  def IsAuthenticated(f: => String => Request[AnyContent] => Future[SimpleResult]) = Security.Authenticated(username, onUnauthorized) { user =>
    Action.async { request =>
      username(request).map { login =>
        f(login)(request)
      }.getOrElse(Future.successful(onUnauthorized(request)))
    }
  }

}

