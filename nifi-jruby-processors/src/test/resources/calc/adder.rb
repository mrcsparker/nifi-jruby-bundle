require_relative 'math_string'

class Adder
  def initialize

  end

  def add(a, b)
    a + b
  end

  def add_string(a, b)
    math_string = MathString.new
    math_string.make_string(add(a, b))
  end
end