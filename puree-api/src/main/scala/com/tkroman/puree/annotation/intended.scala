package com.tkroman.puree.annotation

import scala.annotation.StaticAnnotation

class intended extends StaticAnnotation {
  def this(reason: String) = this()
}
