fun box(): String  {
    var  i: Int = 10
    while (i > 0) {
        i = i - 1
    }
    do {
        i = i + 1
    } while (i <= 10)
    val  tmp0_iterator: IntIterator = 0.rangeTo(i).iterator()
    while (tmp0_iterator.hasNext()) {
        val  j: Int = tmp0_iterator.next()
        val  t: Int = j + 1
    }
    return "OK"
}
