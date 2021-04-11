import com.squareup.moshi.Moshi
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert
import org.junit.Test

internal class ClassDelegateAdapterTest {
    private val moshi = Moshi.Builder()
        .add(FactoryCreate<Student>(Person::class, Identifiable::class))
        .add(FactoryCreate<StudentNoId>(Person::class))
        .build()

    @Test
    fun `parse object with one class delegate`() {
        val person = moshi.adapter(StudentNoId::class.java).fromJson(jsonStudent)
        MatcherAssert.assertThat(
            person, `is`(
                StudentNoId(
                    PersonData("studentName", "studentSurname", 20),
                    "universityName",
                    true,
                    listOf("Maths", "Geography", "History"),
                    Book("Learning to Code")
                )
            )
        )
    }

    @Test
    fun `parse object with two class delegates`() {
        val person = moshi.adapter(Student::class.java).fromJson(jsonStudentWithId)
        MatcherAssert.assertThat(
            person, `is`(
                Student(
                    PersonId("abcd-1234"),
                    PersonData("studentName", "studentSurname", 20),
                    "universityName",
                    true,
                    listOf("Maths", "Geography", "History"),
                    Book("Learning to Code")
                )
            )
        )
    }
}